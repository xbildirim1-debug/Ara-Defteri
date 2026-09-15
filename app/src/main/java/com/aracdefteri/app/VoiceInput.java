package com.aracdefteri.app;

import android.content.Intent;
import android.speech.RecognizerIntent;

import java.util.ArrayList;
import java.util.Locale;

/** Android'in kurulu konuşma tanıma sağlayıcısını kullanır; sonuç otomatik kaydedilmez. */
public final class VoiceInput {
    public static final int REQUEST_CODE = 3112;

    private VoiceInput() { }

    public static Intent createIntent() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, new Locale("tr", "TR").toLanguageTag());
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, new Locale("tr", "TR").toLanguageTag());
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Kaydı anlat: örn. 500 lira benzin, 125 bin km, Opet'ten");
        return i;
    }

    public static String extract(Intent data) {
        if (data == null) return "";
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return "";
        return results.get(0) == null ? "" : results.get(0).trim();
    }
}
