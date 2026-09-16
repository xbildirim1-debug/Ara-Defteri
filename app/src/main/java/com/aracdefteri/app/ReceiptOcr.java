package com.aracdefteri.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.io.InputStream;

/**
 * Cihaz üzerinde çalışan OCR katmanı.
 * Fotoğraf ve çok sayfalı PDF belgeleri destekler; ham belge metnini kalıcı olarak saklamaz.
 */
public final class ReceiptOcr {
    private static final int MAX_PDF_PAGES = 20;
    private ReceiptOcr() { }

    public interface Callback {
        void onResult(RecordParser.Parsed result);
        void onError(Exception error);
    }

    public static void process(Context context, Uri uri, Callback callback) {
        String mime = null;
        try { mime = context.getContentResolver().getType(uri); }
        catch (Exception ignored) { }
        boolean pdf = (mime != null && mime.toLowerCase().contains("pdf")) ||
                (uri != null && uri.toString().toLowerCase().endsWith(".pdf"));
        if (pdf) processPdf(context, uri, callback);
        else processImage(context, uri, callback);
    }

    private static void processImage(Context context, Uri imageUri, Callback callback) {
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        String original = text == null ? "" : text.getText().trim();
                        // Fiş/gösterge fotoğraflarında küçük LCD ve termal yazılar ilk geçişte
                        // kaçabiliyor. İkinci, yüksek kontrastlı geçişi birleştiriyoruz.
                        processEnhancedImage(context, imageUri, recognizer, original, callback);
                    })
                    .addOnFailureListener(error -> {
                        try { callback.onError(error); }
                        finally { recognizer.close(); }
                    });
        } catch (IOException e) {
            callback.onError(e);
        }
    }

    private static void processEnhancedImage(Context context, Uri imageUri, TextRecognizer recognizer,
                                             String originalText, Callback callback) {
        Bitmap source = null;
        Bitmap scaled = null;
        Bitmap enhanced = null;
        try {
            try (InputStream in = context.getContentResolver().openInputStream(imageUri)) {
                source = BitmapFactory.decodeStream(in);
            }
            if (source == null) {
                finishImageRecognition(recognizer, originalText, "", callback);
                return;
            }
            int maxWidth = 2000;
            if (source.getWidth() > maxWidth) {
                int h = Math.max(1, Math.round(source.getHeight() * (maxWidth / (float) source.getWidth())));
                scaled = Bitmap.createScaledBitmap(source, maxWidth, h, true);
            } else {
                scaled = source;
            }
            enhanced = Bitmap.createBitmap(scaled.getWidth(), scaled.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(enhanced);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            ColorMatrix saturation = new ColorMatrix();
            saturation.setSaturation(0f);
            ColorMatrix contrast = new ColorMatrix(new float[]{
                    1.75f,0,0,0,-95,
                    0,1.75f,0,0,-95,
                    0,0,1.75f,0,-95,
                    0,0,0,1,0
            });
            saturation.postConcat(contrast);
            paint.setColorFilter(new ColorMatrixColorFilter(saturation));
            canvas.drawBitmap(scaled, 0, 0, paint);

            final Bitmap finalEnhanced = enhanced;
            final Bitmap finalSource = source;
            final Bitmap finalScaled = scaled;
            recognizer.process(InputImage.fromBitmap(finalEnhanced, 0))
                    .addOnSuccessListener(text -> {
                        String enhancedText = text == null ? "" : text.getText().trim();
                        recycle(finalEnhanced, finalScaled, finalSource);
                        finishImageRecognition(recognizer, originalText, enhancedText, callback);
                    })
                    .addOnFailureListener(error -> {
                        recycle(finalEnhanced, finalScaled, finalSource);
                        finishImageRecognition(recognizer, originalText, "", callback);
                    });
        } catch (Exception e) {
            recycle(enhanced, scaled, source);
            finishImageRecognition(recognizer, originalText, "", callback);
        }
    }

    private static void finishImageRecognition(TextRecognizer recognizer, String original, String enhanced, Callback callback) {
        try {
            String combined;
            if (original == null) original = "";
            if (enhanced == null) enhanced = "";
            if (original.trim().isEmpty()) combined = enhanced.trim();
            else if (enhanced.trim().isEmpty() || enhanced.trim().equals(original.trim())) combined = original.trim();
            else combined = original.trim() + "\n--- IKINCI OKUMA ---\n" + enhanced.trim();
            if (combined.isEmpty()) callback.onError(new IOException("Belgede okunabilir metin bulunamadı"));
            else callback.onResult(RecordParser.fromText(combined));
        } finally {
            recognizer.close();
        }
    }

    private static void recycle(Bitmap enhanced, Bitmap scaled, Bitmap source) {
        if (enhanced != null && !enhanced.isRecycled()) enhanced.recycle();
        if (scaled != null && scaled != source && !scaled.isRecycled()) scaled.recycle();
        if (source != null && !source.isRecycled()) source.recycle();
    }

    private static void processPdf(Context context, Uri pdfUri, Callback callback) {
        ParcelFileDescriptor pfd = null;
        PdfRenderer renderer = null;
        TextRecognizer recognizer = null;
        try {
            pfd = context.getContentResolver().openFileDescriptor(pdfUri, "r");
            if (pfd == null) throw new IOException("PDF açılamadı");
            renderer = new PdfRenderer(pfd);
            if (renderer.getPageCount() <= 0) throw new IOException("PDF sayfası bulunamadı");
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            StringBuilder all = new StringBuilder();
            PdfState state = new PdfState(renderer, pfd, recognizer, all, callback);
            processPdfPage(state, 0);
        } catch (Exception e) {
            closeQuietly(renderer, pfd, recognizer);
            callback.onError(e instanceof Exception ? (Exception) e : new IOException("PDF okunamadı"));
        }
    }

    private static final class PdfState {
        final PdfRenderer renderer;
        final ParcelFileDescriptor pfd;
        final TextRecognizer recognizer;
        final StringBuilder all;
        final Callback callback;
        PdfState(PdfRenderer renderer, ParcelFileDescriptor pfd, TextRecognizer recognizer,
                 StringBuilder all, Callback callback) {
            this.renderer = renderer;
            this.pfd = pfd;
            this.recognizer = recognizer;
            this.all = all;
            this.callback = callback;
        }
    }

    private static void processPdfPage(PdfState state, int index) {
        int count = Math.min(state.renderer.getPageCount(), MAX_PDF_PAGES);
        if (index >= count) {
            try {
                String text = state.all.toString().trim();
                if (text.isEmpty()) state.callback.onError(new IOException("PDF'de okunabilir metin bulunamadı"));
                else state.callback.onResult(RecordParser.fromText(text));
            } finally {
                closeQuietly(state.renderer, state.pfd, state.recognizer);
            }
            return;
        }

        PdfRenderer.Page page = null;
        Bitmap bitmap = null;
        try {
            page = state.renderer.openPage(index);
            int targetWidth = Math.max(1200, Math.min(2200, page.getWidth() * 2));
            float scale = targetWidth / (float) page.getWidth();
            int targetHeight = Math.max(1, Math.round(page.getHeight() * scale));
            bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(android.graphics.Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            page = null;

            final Bitmap pageBitmap = bitmap;
            InputImage image = InputImage.fromBitmap(pageBitmap, 0);
            state.recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        if (text != null && !text.getText().trim().isEmpty()) {
                            if (state.all.length() > 0) state.all.append("\n--- SAYFA ---\n");
                            state.all.append(text.getText());
                        }
                        pageBitmap.recycle();
                        processPdfPage(state, index + 1);
                    })
                    .addOnFailureListener(error -> {
                        pageBitmap.recycle();
                        closeQuietly(state.renderer, state.pfd, state.recognizer);
                        state.callback.onError(error);
                    });
        } catch (Exception e) {
            if (page != null) try { page.close(); } catch (Exception ignored) { }
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            closeQuietly(state.renderer, state.pfd, state.recognizer);
            state.callback.onError(e instanceof Exception ? (Exception) e : new IOException("PDF sayfası okunamadı"));
        }
    }

    private static void closeQuietly(PdfRenderer renderer, ParcelFileDescriptor pfd, TextRecognizer recognizer) {
        if (recognizer != null) try { recognizer.close(); } catch (Exception ignored) { }
        if (renderer != null) try { renderer.close(); } catch (Exception ignored) { }
        if (pfd != null) try { pfd.close(); } catch (Exception ignored) { }
    }
}
