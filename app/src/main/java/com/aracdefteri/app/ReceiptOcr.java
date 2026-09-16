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
                processFocusedImage(context, imageUri, recognizer, originalText, "", callback);
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
                        processFocusedImage(context, imageUri, recognizer, originalText, enhancedText, callback);
                    })
                    .addOnFailureListener(error -> {
                        recycle(finalEnhanced, finalScaled, finalSource);
                        processFocusedImage(context, imageUri, recognizer, originalText, "", callback);
                    });
        } catch (Exception e) {
            recycle(enhanced, scaled, source);
            processFocusedImage(context, imageUri, recognizer, originalText, "", callback);
        }
    }

    private static void processFocusedImage(Context context, Uri imageUri, TextRecognizer recognizer,
                                            String original, String enhanced, Callback callback) {
        Bitmap source = null;
        try {
            try (InputStream in = context.getContentResolver().openInputStream(imageUri)) {
                source = BitmapFactory.decodeStream(in);
            }
            if (source == null) {
                finishImageRecognition(context, imageUri, recognizer, original, enhanced, "", "", callback);
                return;
            }
            int maxWidth = 1800;
            Bitmap work = source;
            if (source.getWidth() > maxWidth) {
                int h = Math.max(1, Math.round(source.getHeight() * (maxWidth / (float) source.getWidth())));
                work = Bitmap.createScaledBitmap(source, maxWidth, h, true);
            }
            final Bitmap finalSource = source;
            final Bitmap finalWork = work;
            final Bitmap binary = binaryBitmap(work);
            recognizer.process(InputImage.fromBitmap(binary, 0))
                    .addOnSuccessListener(t -> {
                        String binaryText = t == null ? "" : t.getText().trim();
                        int x = Math.max(0, (int)(finalWork.getWidth() * .22f));
                        int y = Math.max(0, (int)(finalWork.getHeight() * .48f));
                        int w = Math.max(1, Math.min(finalWork.getWidth() - x, (int)(finalWork.getWidth() * .56f)));
                        int h = Math.max(1, Math.min(finalWork.getHeight() - y, (int)(finalWork.getHeight() * .30f)));
                        Bitmap crop = Bitmap.createBitmap(finalWork, x, y, w, h);
                        Bitmap crop2 = Bitmap.createScaledBitmap(crop, Math.min(1800, crop.getWidth()*2), Math.min(900, crop.getHeight()*2), true);
                        crop.recycle();
                        recognizer.process(InputImage.fromBitmap(crop2, 0))
                                .addOnSuccessListener(ct -> {
                                    String focus = ct == null ? "" : ct.getText().trim();
                                    crop2.recycle(); binary.recycle();
                                    if (finalWork != finalSource && !finalWork.isRecycled()) finalWork.recycle();
                                    if (!finalSource.isRecycled()) finalSource.recycle();
                                    finishImageRecognition(context, imageUri, recognizer, original, enhanced, binaryText, focus, callback);
                                })
                                .addOnFailureListener(e -> {
                                    crop2.recycle(); binary.recycle();
                                    if (finalWork != finalSource && !finalWork.isRecycled()) finalWork.recycle();
                                    if (!finalSource.isRecycled()) finalSource.recycle();
                                    finishImageRecognition(context, imageUri, recognizer, original, enhanced, binaryText, "", callback);
                                });
                    })
                    .addOnFailureListener(e -> {
                        binary.recycle();
                        if (finalWork != finalSource && !finalWork.isRecycled()) finalWork.recycle();
                        if (!finalSource.isRecycled()) finalSource.recycle();
                        finishImageRecognition(context, imageUri, recognizer, original, enhanced, "", "", callback);
                    });
        } catch (Exception e) {
            if (source != null && !source.isRecycled()) source.recycle();
            finishImageRecognition(context, imageUri, recognizer, original, enhanced, "", "", callback);
        }
    }

    private static Bitmap binaryBitmap(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        int w=src.getWidth(), h=src.getHeight();
        int stepSample=Math.max(1, Math.min(w,h)/500);
        long sum=0; long count=0;
        for(int y=0;y<h;y+=stepSample) for(int x=0;x<w;x+=stepSample){
            int c=src.getPixel(x,y); sum += (android.graphics.Color.red(c)*30 + android.graphics.Color.green(c)*59 + android.graphics.Color.blue(c)*11)/100; count++; }
        int threshold = count==0 ? 155 : (int)(sum/count);
        threshold = Math.max(115, Math.min(195, threshold));
        int[] row=new int[w];
        for(int y=0;y<h;y++){
            for(int x=0;x<w;x++){
                int c=src.getPixel(x,y); int lum=(android.graphics.Color.red(c)*30 + android.graphics.Color.green(c)*59 + android.graphics.Color.blue(c)*11)/100;
                row[x]= lum < threshold ? android.graphics.Color.BLACK : android.graphics.Color.WHITE;
            }
            out.setPixels(row,0,w,0,y,w,1);
        }
        return out;
    }

    private static void finishImageRecognition(Context context, Uri imageUri, TextRecognizer recognizer,
                                               String original, String enhanced, String binary, String focus,
                                               Callback callback) {
        try {
            StringBuilder all = new StringBuilder();
            if (original != null && !original.trim().isEmpty()) all.append(original.trim());
            if (enhanced != null && !enhanced.trim().isEmpty() && !enhanced.trim().equals(original == null ? "" : original.trim())) {
                if (all.length()>0) all.append("\n--- IKINCI_OKUMA ---\n"); all.append(enhanced.trim());
            }
            if (binary != null && !binary.trim().isEmpty()) {
                if (all.length()>0) all.append("\n--- YUKSEK_KONTRAST ---\n"); all.append(binary.trim());
            }
            if (focus != null && !focus.trim().isEmpty()) {
                if (all.length()>0) all.append("\n--- ODOMETRE_BOLGESI ---\n"); all.append(focus.trim());
            }
            String combined = all.toString().trim();
            if (combined.isEmpty()) { callback.onError(new IOException("Belgede okunabilir metin bulunamadı")); return; }
            RecordParser.Parsed parsed = RecordParser.fromText(combined);
            Bitmap visual = null;
            try {
                try (InputStream in = context.getContentResolver().openInputStream(imageUri)) { visual = BitmapFactory.decodeStream(in); }
                if (visual != null && visual.getWidth() > 1800) {
                    int hh=Math.max(1,Math.round(visual.getHeight()*(1800f/visual.getWidth())));
                    Bitmap scaled=Bitmap.createScaledBitmap(visual,1800,hh,true); visual.recycle(); visual=scaled;
                }
                VisualDocumentAnalyzer.applyImage(visual, combined, parsed);
            } catch (Exception ignored) {
                VisualDocumentAnalyzer.sanitizeTextOcr(combined, parsed);
            } finally {
                if (visual != null && !visual.isRecycled()) visual.recycle();
            }
            callback.onResult(parsed);
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
                else {
                    RecordParser.Parsed parsed = RecordParser.fromText(text);
                    VisualDocumentAnalyzer.sanitizeTextOcr(text, parsed);
                    state.callback.onResult(parsed);
                }
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
