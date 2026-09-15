package com.aracdefteri.app;

import android.content.Context;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;

/** Cihaz üzerinde çalışan, ham belge metnini kalıcı olarak saklamayan OCR katmanı. */
public final class ReceiptOcr {
    private ReceiptOcr() { }

    public interface Callback {
        void onResult(RecordParser.Parsed result);
        void onError(Exception error);
    }

    public static void process(Context context, Uri imageUri, Callback callback) {
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        try {
                            callback.onResult(RecordParser.fromText(text.getText()));
                        } finally {
                            recognizer.close();
                        }
                    })
                    .addOnFailureListener(error -> {
                        try {
                            callback.onError(error);
                        } finally {
                            recognizer.close();
                        }
                    });
        } catch (IOException e) {
            callback.onError(e);
        }
    }
}
