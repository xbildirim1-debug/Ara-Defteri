package com.aracdefteri.app;

import android.content.Intent;
import android.speech.RecognizerIntent;

import java.util.ArrayList;

/** Uygulama içi sürekli ses oturumunu başlatır; sonuç otomatik kaydedilmez. */
public final class VoiceInput {
    public static final int REQUEST_CODE = 3112;
    public static final String ACTION_CAPTURE = "com.aracdefteri.app.action.VOICE_CAPTURE";

    private VoiceInput() { }

    public static Intent createIntent() {
        Intent i = new Intent(ACTION_CAPTURE);
        i.setPackage("com.aracdefteri.app");
        return i;
    }

    public static String extract(Intent data) {
        if (data == null) return "";
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return "";
        return results.get(0) == null ? "" : results.get(0).trim();
    }
}
