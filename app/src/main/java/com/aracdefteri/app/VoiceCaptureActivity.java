package com.aracdefteri.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Uygulama içi sürekli konuşma oturumu.
 *
 * Android konuşma sağlayıcısı kısa bir sessizlikte bir segmenti sonlandırsa bile
 * oturum kapanmaz; segment kaydedilir ve dinleme otomatik yeniden başlatılır.
 * Kullanıcı "Bitir ve kullan" dediğinde tüm segmentler tek metin olarak çağıran
 * ekrana döndürülür. Böylece örneğin yakıt + kilometre aynı sesli girişte işlenir.
 */
public class VoiceCaptureActivity extends Activity implements RecognitionListener {
    private static final int REQ_AUDIO = 6112;
    private static final long RESTART_DELAY_MS = 350L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<String> committedSegments = new ArrayList<>();

    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private TextView statusView;
    private TextView transcriptView;
    private Button finishButton;

    private boolean sessionFinished = false;
    private boolean listening = false;
    private String lastPartial = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusView.setText("Bu cihazda konuşma tanıma hizmeti bulunamadı.");
            finishButton.setEnabled(false);
            Toast.makeText(this, "Konuşma tanıma hizmeti bulunamadı", Toast.LENGTH_LONG).show();
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        } else {
            startSession();
        }
    }

    private void buildUi() {
        int bg = Color.rgb(8, 13, 14);
        int surface = Color.rgb(20, 28, 29);
        int text = Color.rgb(245, 250, 248);
        int muted = Color.rgb(159, 176, 171);
        int accent = Color.rgb(42, 213, 163);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(22));
        root.setBackgroundColor(bg);

        TextView title = label("Sesli veri girişi", text, 24, true);
        root.addView(title);

        TextView help = label("Doğal şekilde konuş. Duraksayabilirsin; dinleme kapanmaz. Örnek: “10 litre benzin aldım, 873 lira… kilometre de 235 bin 487.”", muted, 13, false);
        help.setPadding(0, dp(8), 0, dp(18));
        root.addView(help);

        statusView = label("Hazırlanıyor…", accent, 14, true);
        statusView.setPadding(dp(14), dp(13), dp(14), dp(13));
        statusView.setBackground(round(surface, dp(16), accent));
        root.addView(statusView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView caption = label("Algılanan konuşma", muted, 11, true);
        caption.setPadding(0, dp(20), 0, dp(7));
        root.addView(caption);

        ScrollView scroll = new ScrollView(this);
        transcriptView = label("Henüz bir şey söylemedin.", text, 16, false);
        transcriptView.setPadding(dp(15), dp(14), dp(15), dp(14));
        transcriptView.setBackground(round(surface, dp(16), Color.rgb(53, 69, 68)));
        scroll.addView(transcriptView);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        finishButton = new Button(this);
        finishButton.setText("Bitir ve kullan");
        finishButton.setTextSize(15);
        finishButton.setTextColor(Color.rgb(4, 33, 27));
        finishButton.setAllCaps(false);
        finishButton.setBackground(round(accent, dp(16), Color.TRANSPARENT));
        finishButton.setOnClickListener(v -> finishAndReturn());
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        fp.setMargins(0, dp(18), 0, 0);
        root.addView(finishButton, fp);

        Button cancel = new Button(this);
        cancel.setText("Vazgeç");
        cancel.setTextSize(14);
        cancel.setTextColor(muted);
        cancel.setAllCaps(false);
        cancel.setBackgroundColor(Color.TRANSPARENT);
        cancel.setOnClickListener(v -> cancelSession());
        root.addView(cancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        setContentView(root);
    }

    private void startSession() {
        if (sessionFinished) return;
        try {
            if (recognizer != null) recognizer.destroy();
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);

            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, new Locale("tr", "TR").toLanguageTag());
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, new Locale("tr", "TR").toLanguageTag());
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8500L);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6500L);

            statusView.setText("Dinliyorum… Duraksayabilirsin, ben bekliyorum.");
            startListeningNow();
        } catch (Exception e) {
            statusView.setText("Mikrofon başlatılamadı. Tekrar deneyebilirsin.");
        }
    }

    private void startListeningNow() {
        if (sessionFinished || recognizer == null || listening) return;
        try {
            lastPartial = "";
            listening = true;
            recognizer.startListening(recognizerIntent);
            statusView.setText("Dinliyorum… Duraksayabilirsin, ben bekliyorum.");
        } catch (Exception e) {
            listening = false;
            scheduleRestart(700L);
        }
    }

    private void scheduleRestart(long delay) {
        if (sessionFinished) return;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::startListeningNow, delay);
    }

    private void commitSegment(String segment) {
        String s = clean(segment);
        if (s.isEmpty()) return;
        if (!committedSegments.isEmpty()) {
            String last = clean(committedSegments.get(committedSegments.size() - 1));
            if (last.equalsIgnoreCase(s)) return;
            if (last.length() > 8 && s.toLowerCase(Locale.ROOT).contains(last.toLowerCase(Locale.ROOT))) {
                committedSegments.set(committedSegments.size() - 1, s);
                updateTranscript("");
                return;
            }
        }
        committedSegments.add(s);
        updateTranscript("");
    }

    private void updateTranscript(String partial) {
        StringBuilder b = new StringBuilder();
        for (String s : committedSegments) {
            if (b.length() > 0) b.append("\n");
            b.append(s);
        }
        String p = clean(partial);
        if (!p.isEmpty()) {
            if (b.length() > 0) b.append("\n");
            b.append(p);
        }
        transcriptView.setText(b.length() == 0 ? "Henüz bir şey söylemedin." : b.toString());
    }

    private String combinedTranscript() {
        ArrayList<String> all = new ArrayList<>(committedSegments);
        String p = clean(lastPartial);
        if (!p.isEmpty()) {
            boolean duplicate = false;
            for (String s : all) if (clean(s).equalsIgnoreCase(p)) { duplicate = true; break; }
            if (!duplicate) all.add(p);
        }
        StringBuilder b = new StringBuilder();
        for (String s : all) {
            if (clean(s).isEmpty()) continue;
            if (b.length() > 0) b.append(". ");
            b.append(clean(s));
        }
        return b.toString().trim();
    }

    private void finishAndReturn() {
        if (sessionFinished) return;
        sessionFinished = true;
        handler.removeCallbacksAndMessages(null);
        listening = false;
        try { if (recognizer != null) recognizer.cancel(); } catch (Exception ignored) { }

        String combined = combinedTranscript();
        if (combined.isEmpty()) {
            Toast.makeText(this, "Henüz anlaşılır bir konuşma alınmadı", Toast.LENGTH_SHORT).show();
            sessionFinished = false;
            scheduleRestart(300L);
            return;
        }

        ArrayList<String> results = new ArrayList<>();
        results.add(combined);
        Intent out = new Intent();
        out.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, results);
        setResult(RESULT_OK, out);
        finish();
    }

    private void cancelSession() {
        sessionFinished = true;
        handler.removeCallbacksAndMessages(null);
        try { if (recognizer != null) recognizer.cancel(); } catch (Exception ignored) { }
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_AUDIO) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startSession();
        } else {
            Toast.makeText(this, "Sesli giriş için mikrofon izni gerekli", Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override public void onReadyForSpeech(Bundle params) {
        statusView.setText("Dinliyorum… Konuşmaya devam edebilirsin.");
    }
    @Override public void onBeginningOfSpeech() {
        statusView.setText("Seni duyuyorum…");
    }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() {
        listening = false;
        statusView.setText("Kısa duraksama algılandı… Dinlemeye devam edeceğim.");
    }

    @Override
    public void onError(int error) {
        listening = false;
        if (sessionFinished) return;
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            statusView.setText("Mikrofon izni gerekli.");
            return;
        }
        // Sağlayıcı sessizlikte segmenti hata ile kapatırsa partial sonucu kaybetme.
        if (!clean(lastPartial).isEmpty()) {
            commitSegment(lastPartial);
            lastPartial = "";
        }
        // NO_MATCH ve SPEECH_TIMEOUT normal duraksama gibi ele alınır.
        // RECOGNIZER_BUSY/CLIENT için biraz daha uzun bekleyip yeniden başlarız.
        long delay = (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) ? 900L : RESTART_DELAY_MS;
        statusView.setText("Dinlemeye devam ediyorum… Bitir ve kullan diyene kadar kapanmayacağım.");
        scheduleRestart(delay);
    }

    @Override
    public void onResults(Bundle results) {
        listening = false;
        ArrayList<String> matches = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) commitSegment(matches.get(0));
        lastPartial = "";
        if (!sessionFinished) {
            statusView.setText("Devam edebilirsin…");
            scheduleRestart(RESTART_DELAY_MS);
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> matches = partialResults == null ? null : partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) return;
        lastPartial = clean(matches.get(0));
        updateTranscript(lastPartial);
    }

    @Override public void onEvent(int eventType, Bundle params) { }

    @Override
    protected void onDestroy() {
        sessionFinished = true;
        handler.removeCallbacksAndMessages(null);
        try { if (recognizer != null) recognizer.destroy(); } catch (Exception ignored) { }
        recognizer = null;
        super.onDestroy();
    }

    private TextView label(String value, int color, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(sp);
        if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        v.setGravity(Gravity.START);
        return v;
    }

    private GradientDrawable round(int color, int radius, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (strokeColor != Color.TRANSPARENT) g.setStroke(dp(1), strokeColor);
        return g;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
