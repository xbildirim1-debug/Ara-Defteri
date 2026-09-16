package com.aracdefteri.app;

import android.content.Intent;
import android.speech.RecognizerIntent;

import java.util.ArrayList;

/** Uygulama içi sürekli ses oturumunu başlatır; sonuç otomatik kaydedilmez. */
public final class VoiceInput {
    public static final int REQUEST_CODE = 3112;

    private VoiceInput() { }

    public static Intent createIntent() {
        Intent i = new Intent();
        i.setClassName("com.aracdefteri.app", "com.aracdefteri.app.VoiceCaptureActivity");
        return i;
    }

    public static String extract(Intent data) {
        if (data == null) return "";
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return "";
        return results.get(0) == null ? "" : results.get(0).trim();
    }
}
