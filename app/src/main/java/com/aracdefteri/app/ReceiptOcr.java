package com.aracdefteri.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;

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
                        try {
                            if (text == null || text.getText().trim().isEmpty()) {
                                callback.onError(new IOException("Belgede okunabilir metin bulunamadı"));
                            } else {
                                callback.onResult(RecordParser.fromText(text.getText()));
                            }
                        } finally {
                            recognizer.close();
                        }
                    })
                    .addOnFailureListener(error -> {
                        try { callback.onError(error); }
                        finally { recognizer.close(); }
                    });
        } catch (IOException e) {
            callback.onError(e);
        }
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
