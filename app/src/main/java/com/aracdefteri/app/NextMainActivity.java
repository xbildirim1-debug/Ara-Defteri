package com.aracdefteri.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NextMainActivity extends Activity {
    private static final String PREFS = "arac_defteri_prefs";
    private static final int PICK_GALLERY_IMAGE = 3101;
    private static final int PICK_RECORD_ATTACHMENT = 3102;
    private static final int PICK_VEHICLE_IMAGE = 3103;
    private static final int PICK_CV_IMAGES = 3104;
    private static final int PICK_DAMAGE_IMAGES = 3105;
    private static final int PICK_OCR_IMAGE = 3110;
    private static final int CAPTURE_OCR_IMAGE = 3111;

    private AppDatabase db;
    private SharedPreferences prefs;
    private LinearLayout pageHost, navBar;
    private int currentPage = 0;
    private String currentModule = null;
    private long selectedRecordId = -1;
    private long pendingAttachmentRecordId = -1;
    private long pendingDamagePhotoRecordId = -1;
    private boolean dark;
    private final ArrayList<Uri> cvPhotoUris = new ArrayList<>();
    private TextView cvPhotoCountView;
    private FormRefs pendingSmartForm;
    private String pendingSmartModule;
    private Uri pendingOcrCameraUri;
    private boolean assistantInputActive = false;
    private TextView assistantStatus;

    private int bg, surface, surface2, text, muted, accent, accentSoft, warning, danger, stroke, success;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        db = new AppDatabase(this);
        if (!prefs.getBoolean("legacy_demo_cleanup_v12", false)) {
            db.clearLegacyDemoIfPresent();
            prefs.edit().putBoolean("legacy_demo_cleanup_v12", true).apply();
        }
        resolveTheme();
        ReminderScheduler.ensureChannel(this);
        requestNotificationPermissionIfNeeded();
        renderShell();
        renderPage(0);
    }

    private void resolveTheme() {
        String mode = prefs.getString("theme_mode", "system");
        if ("dark".equals(mode)) dark = true;
        else if ("light".equals(mode)) dark = false;
        else dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        if (dark) {
            bg = Color.rgb(7, 10, 12);
            surface = Color.rgb(17, 23, 25);
            surface2 = Color.rgb(27, 36, 38);
            text = Color.rgb(246, 250, 249);
            muted = Color.rgb(150, 166, 163);
            accent = Color.rgb(42, 213, 163);
            accentSoft = Color.rgb(24, 77, 64);
            warning = Color.rgb(255, 190, 84);
            danger = Color.rgb(255, 104, 122);
            stroke = Color.rgb(44, 58, 60);
            success = Color.rgb(82, 220, 145);
        } else {
            bg = Color.rgb(245, 248, 247);
            surface = Color.WHITE;
            surface2 = Color.rgb(234, 242, 239);
            text = Color.rgb(18, 31, 28);
            muted = Color.rgb(92, 111, 106);
            accent = Color.rgb(8, 163, 118);
            accentSoft = Color.rgb(218, 244, 235);
            warning = Color.rgb(204, 126, 18);
            danger = Color.rgb(213, 66, 86);
            stroke = Color.rgb(214, 227, 223);
            success = Color.rgb(21, 153, 92);
        }
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void renderShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        pageHost = new LinearLayout(this);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(pageHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(dp(8), dp(6), dp(8), dp(7));
        navBar.setBackground(cardDrawable(surface, 0, stroke));
        root.addView(navBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70)));
        setContentView(root);
        buildBottomNav();
    }

private void buildBottomNav() {
        navBar.removeAllViews();
        String[] labels = {"Ana Sayfa", "Kayıtlar", "Asistan", "Araç CV", "Ayarlar"};
        int[] icons = {R.drawable.ic_nav_home, R.drawable.ic_nav_records, R.drawable.ic_nav_assistant, R.drawable.ic_nav_cv, R.drawable.ic_nav_settings};
        for (int i = 0; i < labels.length; i++) {
            final int page = i;
            boolean active = currentModule == null && currentPage == i && selectedRecordId < 0;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(5), dp(5), dp(5), dp(3));
            if (active) item.setBackground(cardDrawable(accentSoft, dp(18), Color.TRANSPARENT));
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setColorFilter(active ? accent : muted);
            item.addView(icon, new LinearLayout.LayoutParams(dp(23), dp(23)));
            TextView label = tv(labels[i], active ? text : muted, 9, active);
            label.setGravity(Gravity.CENTER);
            item.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(19)));
            item.setOnClickListener(v -> renderPage(page));
            navBar.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    private LinearLayout newContent() {
        pageHost.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(28));
        scroll.addView(content);
        pageHost.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return content;
    }

private void renderPage(int page) {
        pendingSmartForm = null;
        pendingSmartModule = null;
        currentPage = page;
        currentModule = null;
        selectedRecordId = -1;
        buildBottomNav();
        LinearLayout content = newContent();
        if (page == 0) renderHome(content);
        else if (page == 1) renderRecordsHub(content);
        else if (page == 2) renderAssistant(content);
        else if (page == 3) renderCv(content);
        else renderSettings(content);
    }

    private void openModule(String module) {
        pendingSmartForm = null;
        pendingSmartModule = null;
        currentPage = 1;
        currentModule = module;
        selectedRecordId = -1;
        buildBottomNav();
        LinearLayout content = newContent();
        if ("Giderler".equals(module)) renderExpenses(content);
        else renderModule(content, module);
    }

    @Override
    public void onBackPressed() {
        if (selectedRecordId >= 0 && currentModule != null) openModule(currentModule);
        else if (currentModule != null) renderPage(1);
        else super.onBackPressed();
    }

    private void renderHome(LinearLayout content) {
        addHeader(content, "Taşıtım", "Taşıtının dijital hafızası");
        AppDatabase.Vehicle vehicle = db.getVehicle();
        if (vehicle.brand == null || vehicle.brand.trim().isEmpty() || vehicle.model == null || vehicle.model.trim().isEmpty()) {
            LinearLayout emptyVehicle = infoCard("Henüz araç eklenmedi", "Araç bilgilerini eklediğinde kilometre, kayıtlar ve Araç CV burada oluşmaya başlar.", accent);
            content.addView(emptyVehicle);
            gap(content, 10);
            Button addVehicle = primaryButton("Araç bilgilerini ekle");
            addVehicle.setOnClickListener(v -> openVehicleForm());
            content.addView(addVehicle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        } else {
            content.addView(vehicleHero(vehicle));
        }
        gap(content, 20);

        sectionTitle(content, "Yaklaşanlar", "Yaklaşan işlem ve bitiş tarihleri");
        List<AppDatabase.Record> upcoming = db.getUpcomingRecords(3);
        if (upcoming.isEmpty()) content.addView(infoCard("Planlı işlem yok", "Bir kayda sonraki tarih veya kilometre eklediğinde burada görünür.", accent));
        else for (AppDatabase.Record r : upcoming) content.addView(upcomingCard(r));

        gap(content, 20);
        sectionTitle(content, "Bu ay", "Aracına ait güncel hareketler");
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(statCard("HARCAMA", formatMoney(db.getCurrentMonthExpenseTotal()), "Bu ay"), new LinearLayout.LayoutParams(0, dp(96), 1f));
        gapHorizontal(stats, 10);
        stats.addView(statCard("KAYIT", String.valueOf(db.countAllRecords()), "Toplam"), new LinearLayout.LayoutParams(0, dp(96), 1f));
        content.addView(stats);

        gap(content, 20);
        sectionTitle(content, "Son hareketler", "Detay, düzenleme ve silme için kayda dokun");
        List<AppDatabase.Record> recent = db.getRecords(4);
        if (recent.isEmpty()) content.addView(infoCard("Henüz kayıt yok", "Bakım, yakıt veya başka bir araç kaydı ekleyerek başla.", accent));
        else for (AppDatabase.Record r : recent) content.addView(recordCard(r));
    }

    private View vehicleHero(AppDatabase.Vehicle v) {
        LinearLayout outer = card();
        outer.setPadding(dp(7), dp(7), dp(7), dp(14));

        FrameLayout media = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                dark ? new int[]{Color.rgb(12, 88, 70), Color.rgb(20, 45, 42)} : new int[]{Color.rgb(14, 181, 131), Color.rgb(4, 112, 102)});
        g.setCornerRadius(dp(24));
        media.setBackground(g);
        if (Build.VERSION.SDK_INT >= 21) media.setClipToOutline(true);

        String uri = prefs.getString("vehicle_photo_uri", "");
        if (!uri.isEmpty()) {
            ImageView photo = new ImageView(this);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try { photo.setImageURI(Uri.parse(uri)); } catch (Exception ignored) {}
            media.addView(photo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            ImageView car = new ImageView(this);
            car.setImageResource(R.drawable.ic_car_hero);
            car.setColorFilter(Color.argb(220,255,255,255));
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(150), dp(90));
            cp.gravity = Gravity.CENTER;
            media.addView(car, cp);
            TextView hint = tv("Araç fotoğrafı ekle", Color.WHITE, 12, true);
            hint.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
            hp.gravity = Gravity.BOTTOM;
            hp.setMargins(dp(12), 0, dp(12), dp(10));
            media.addView(hint, hp);
        }

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(14), dp(10), dp(14), dp(10));
        overlay.setBackground(cardDrawable(Color.argb(175, 5, 10, 10), dp(18), Color.TRANSPARENT));
        overlay.addView(tv(v.year + "  " + v.brand + " " + v.model, Color.WHITE, 19, true));
        overlay.addView(tv(formatInt(v.km) + " km  •  " + v.fuelType, Color.argb(220,255,255,255), 11, false));
        FrameLayout.LayoutParams op = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        op.gravity = Gravity.BOTTOM;
        op.setMargins(dp(10), 0, dp(10), dp(10));
        media.addView(overlay, op);

        TextView camera = tv("＋", Color.WHITE, 22, true);
        camera.setGravity(Gravity.CENTER);
        camera.setBackground(cardDrawable(Color.argb(190, 10, 18, 18), dp(18), Color.TRANSPARENT));
        FrameLayout.LayoutParams cam = new FrameLayout.LayoutParams(dp(42), dp(42));
        cam.gravity = Gravity.TOP | Gravity.RIGHT;
        cam.setMargins(0, dp(12), dp(12), 0);
        media.addView(camera, cam);
        media.setOnClickListener(x -> pickVehicleImage());
        outer.addView(media, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));

        LinearLayout action = new LinearLayout(this);
        action.setOrientation(LinearLayout.HORIZONTAL);
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setPadding(dp(9), dp(12), dp(9), 0);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(tv("Araç bilgileri", text, 13, true));
        left.addView(tv("Marka, model, yakıt ve kilometre", muted, 10, false));
        action.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView edit = pill("Düzenle", accent, accentSoft);
        edit.setOnClickListener(x -> openVehicleForm());
        action.addView(edit);
        outer.addView(action);
        return outer;
    }

    private void renderRecordsHub(LinearLayout content) {
        addHeader(content, "Kayıtlar", "Her konu kendi alanında, karışıklık yok");
        String[] modules = {"Bakım", "Hasar", "Ekspertiz", "Muayene", "Vergi", "Sigorta/Kasko", "Yakıt", "Giderler"};
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        for (String module : modules) addModuleTile(grid, module);
        content.addView(grid);
    }


    private void renderAssistant(LinearLayout content) {
        addHeader(content, "Asistan", "Konuş, fotoğraf çek veya belge seç; kayıt türünü Taşıtım bulsun");

        LinearLayout hero = card();
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));
        hero.addView(tv("Ne yaptığını söylemen yeterli", text, 17, true));
        TextView help = tv("Örnek: “Kilometrem 321.609 oldu.” • “10 litre yakıt aldım, 873 lira.” • “Kasko poliçem 12 Mart 2027'de bitiyor.”", muted, 10, false);
        help.setPadding(0, dp(5), 0, dp(13));
        hero.addView(help);

        Button voice = primaryButton("🎤  Konuşarak ekle");
        voice.setOnClickListener(v -> {
            assistantInputActive = true;
            pendingSmartForm = null;
            pendingSmartModule = null;
            startSmartVoice();
        });
        hero.addView(voice, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        gap(hero, 9);

        LinearLayout imageActions = new LinearLayout(this);
        imageActions.setOrientation(LinearLayout.HORIZONTAL);
        Button camera = secondaryButton("📷 Kamera");
        Button file = secondaryButton("▣ Fotoğraf / PDF");
        camera.setOnClickListener(v -> {
            assistantInputActive = true;
            pendingSmartForm = null;
            pendingSmartModule = null;
            captureOcrImage();
        });
        file.setOnClickListener(v -> {
            assistantInputActive = true;
            pendingSmartForm = null;
            pendingSmartModule = null;
            pickOcrImage();
        });
        imageActions.addView(camera, new LinearLayout.LayoutParams(0, dp(48), 1f));
        gapHorizontal(imageActions, 8);
        imageActions.addView(file, new LinearLayout.LayoutParams(0, dp(48), 1f));
        hero.addView(imageActions);

        assistantStatus = tv("Hazır. Veriyi söyle veya belge/gösterge fotoğrafını okut.", muted, 10, false);
        assistantStatus.setPadding(0, dp(12), 0, 0);
        hero.addView(assistantStatus);
        content.addView(hero);

        gap(content, 16);
        sectionTitle(content, "Neleri anlayabilir?", "Tek merkezden araç kayıtlarını hazırlar");
        content.addView(infoCard("Kilometre ve yakıt", "Gösterge paneli, ODO değeri, yakıt fişi, litre/kWh, birim fiyat ve toplam tutar.", accent));
        gap(content, 8);
        content.addView(infoCard("Bakım ve hasar", "Servis faturası, yapılan işlemler, değişen parçalar, hasar ve onarım bilgileri.", accent));
        gap(content, 8);
        content.addView(infoCard("Muayene, sigorta, vergi, ekspertiz", "Belge türünü ayırır ve uygun kayıt formunu doldurur; kaydetmeden önce sen kontrol edersin.", accent));
    }

    private void handleAssistantResult(RecordParser.Parsed parsed, String source) {
        assistantInputActive = false;
        if (parsed == null) {
            if (assistantStatus != null) assistantStatus.setText(source + " sonucu okunamadı.");
            toast("Veri okunamadı");
            return;
        }

        boolean fuelLike = "FUEL".equals(parsed.documentKind)
                || (parsed.quantity > 0 && (parsed.amount > 0 || parsed.unitPrice > 0))
                || ((parsed.fuelType != null && !parsed.fuelType.trim().isEmpty())
                    && (parsed.quantity > 0 || parsed.amount > 0 || parsed.unitPrice > 0));
        if (fuelLike) {
            parsed.type = "Yakıt";
            parsed.documentKind = "FUEL";
        }

        boolean odometerOnly = !fuelLike && ("ODOMETER".equals(parsed.documentKind) ||
                ((parsed.type == null || parsed.type.trim().isEmpty()) && parsed.km > 0));
        if (odometerOnly) {
            if (parsed.km <= 0) {
                if (assistantStatus != null) assistantStatus.setText("Kilometre değeri güvenle okunamadı. Manuel giriş yapabilirsin.");
                toast("Kilometre okunamadı");
                return;
            }
            AppDatabase.Vehicle current = db.getVehicle();
            String message = "Okunan kilometre: " + formatInt(parsed.km) + " km";
            if (current.km > 0) message += "\nMevcut kilometre: " + formatInt(current.km) + " km";
            new AlertDialog.Builder(this)
                    .setTitle("Kilometreyi güncelle?")
                    .setMessage(message)
                    .setNegativeButton("Vazgeç", null)
                    .setPositiveButton("Güncelle", (d, w) -> {
                        db.updateVehicle(current.brand, current.model, current.year, current.plate, parsed.km, current.fuelType);
                        toast("Kilometre " + formatInt(parsed.km) + " km olarak güncellendi");
                        renderPage(2);
                    })
                    .show();
            return;
        }

        String detected = parsed.type == null ? "" : normalizeModule(parsed.type.trim());
        if (detected.isEmpty()) {
            if (assistantStatus != null) assistantStatus.setText("Kayıt türü belirlenemedi. Daha net söyle veya farklı bir fotoğraf dene.");
            toast("Kayıt türü belirlenemedi");
            return;
        }
        if ("Giderler".equals(detected)) {
            if (assistantStatus != null) assistantStatus.setText("Gider kaydı algılandı; ayrıntıları manuel kontrol et.");
            openExpenseForm(null);
            return;
        }
        openRecordForm(detected, null);
        applyParsedToCurrentForm(parsed, source);
    }

    private void addModuleTile(GridLayout grid, String module) {
        LinearLayout tile = card();
        tile.setPadding(dp(15), dp(15), dp(15), dp(15));
        TextView icon = tv(moduleMark(module), Color.WHITE, 15, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(cardDrawable(moduleColor(module), dp(17), Color.TRANSPARENT));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        gap(tile, 12);
        tile.addView(tv(moduleTitle(module), text, 15, true));
        tile.addView(tv(moduleSubtitle(module), muted, 10, false));
        gap(tile, 10);
        String count = "Giderler".equals(module) ? formatMoney(db.getExpenseTotal()) : db.countRecordsByType(module) + " kayıt";
        tile.addView(tv(count + "   ›", moduleColor(module), 11, true));
        tile.setOnClickListener(v -> openModule(module));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(166);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(0, 0, dp(9), dp(9));
        grid.addView(tile, lp);
    }

    private void renderModule(LinearLayout content, String module) {
        addBackHeader(content, moduleTitle(module), moduleSubtitle(module), () -> renderPage(1));
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.addView(infoChip(db.countRecordsByType(module) + " kayıt"), new LinearLayout.LayoutParams(0, dp(44), 1f));
        gapHorizontal(tools, 9);
        Button add = compactPrimary("+ Yeni kayıt");
        add.setOnClickListener(v -> openRecordForm(module, null));
        tools.addView(add, new LinearLayout.LayoutParams(dp(138), dp(44)));
        content.addView(tools);
        gap(content, 16);

        List<AppDatabase.Record> records = db.getRecordsByType(module);
        if (records.isEmpty()) content.addView(infoCard("Henüz kayıt yok", moduleEmptyText(module), moduleColor(module)));
        else for (AppDatabase.Record r : records) content.addView(recordCard(r));
    }

    private View recordCard(AppDatabase.Record r) {
        LinearLayout c = card();
        c.setPadding(dp(15), dp(14), dp(15), dp(14));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView type = pill(r.type.toUpperCase(Locale.getDefault()), moduleColor(normalizeModule(r.type)), surface2);
        top.addView(type);
        top.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(tv(r.date, muted, 10, false));
        c.addView(top);
        gap(c, 9);
        c.addView(tv(r.title, text, 16, true));
        String meta = formatInt(r.km) + " km";
        if (!r.subtype.isEmpty()) meta += "  •  " + r.subtype;
        if (r.cost > 0) meta += "  •  " + formatMoney(r.cost);
        c.addView(tv(meta, muted, 11, false));
        if ("Yakıt".equals(r.type) && r.quantity > 0) {
            String fuel = trimDouble(r.quantity) + " " + r.unit;
            if (r.unitPrice > 0) fuel += "  •  " + formatMoney(r.unitPrice) + "/" + r.unit;
            TextView f = tv(fuel, accent, 11, true);
            f.setPadding(0, dp(6), 0, 0);
            c.addView(f);
        }
        if (!r.nextDate.isEmpty() || r.nextKm > 0) {
            String next = "Sonraki: " + (!r.nextDate.isEmpty() ? r.nextDate : "") + (!r.nextDate.isEmpty() && r.nextKm > 0 ? " • " : "") + (r.nextKm > 0 ? formatInt(r.nextKm) + " km" : "");
            TextView n = tv(next, warning, 10, true);
            n.setPadding(0, dp(7), 0, 0);
            c.addView(n);
        }
        TextView open = tv("Detayı aç  ›", accent, 11, true);
        open.setPadding(0, dp(9), 0, 0);
        c.addView(open);
        c.setOnClickListener(v -> openRecordDetail(r.id));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        c.setLayoutParams(lp);
        return c;
    }

    private void openRecordDetail(long id) {
        AppDatabase.Record r = db.getRecord(id);
        if (r == null) return;
        currentModule = normalizeModule(r.type);
        currentPage = 1;
        selectedRecordId = id;
        buildBottomNav();
        LinearLayout content = newContent();
        addBackHeader(content, r.title, r.type + " kaydı", () -> openModule(currentModule));

        LinearLayout hero = card();
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.addView(tv(r.type.toUpperCase(Locale.getDefault()), moduleColor(currentModule), 10, true));
        hero.addView(tv(r.title, text, 23, true));
        hero.addView(tv(r.date + "  •  " + formatInt(r.km) + " km", muted, 12, false));
        if (!r.subtype.isEmpty()) detailRow(hero, "Tür / kategori", r.subtype);
        if (!r.status.isEmpty()) detailRow(hero, "Durum", r.status);
        if (r.cost > 0) detailRow(hero, "Tutar", formatMoney(r.cost));
        if (r.quantity > 0) detailRow(hero, "Miktar", trimDouble(r.quantity) + " " + r.unit);
        if (r.unitPrice > 0) detailRow(hero, "Birim fiyat", formatMoney(r.unitPrice) + "/" + r.unit);
        if (!r.extra.isEmpty() && !"Yakıt".equals(r.type)) detailRow(hero, "Ek bilgiler", r.extra.replace("|", ", "));
        if (!r.detail.isEmpty()) detailRow(hero, "Açıklama", r.detail);
        if (!r.nextDate.isEmpty()) detailRow(hero, "Sonraki tarih", r.nextDate);
        if (r.nextKm > 0) detailRow(hero, "Sonraki km", formatInt(r.nextKm) + " km");
        content.addView(hero);
        gap(content, 14);

        if ("Hasar".equals(r.type)) {
            renderDamagePhotos(content, r);
            gap(content, 14);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button edit = secondaryButton("Düzenle");
        edit.setOnClickListener(v -> openRecordForm(currentModule, r));
        actions.addView(edit, new LinearLayout.LayoutParams(0, dp(48), 1f));
        gapHorizontal(actions, 8);
        Button attachment = secondaryButton(r.attachment.isEmpty() ? "Belge ekle" : "Belgeyi aç");
        attachment.setOnClickListener(v -> {
            if (r.attachment.isEmpty()) pickRecordAttachment(r.id);
            else openAttachment(r.attachment);
        });
        actions.addView(attachment, new LinearLayout.LayoutParams(0, dp(48), 1f));
        content.addView(actions);
        gap(content, 9);
        Button delete = dangerButton("Kaydı sil");
        delete.setOnClickListener(v -> confirmDeleteRecord(r));
        content.addView(delete, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
    }

    private void renderDamagePhotos(LinearLayout content, AppDatabase.Record record) {
        List<AppDatabase.RecordPhoto> photos = db.getRecordPhotos(record.id);
        sectionTitle(content, "Hasar fotoğrafları", "Kaza / hasar öncesi ve sonrası fotoğrafları • en fazla 10");

        if (!photos.isEmpty()) {
            android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
            scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (AppDatabase.RecordPhoto photo : photos) row.addView(damagePhotoThumb(record.id, photo));
            scroll.addView(row);
            content.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(106)));
            gap(content, 9);
        } else {
            content.addView(infoCard("Henüz hasar fotoğrafı yok", "Fotoğrafları bu hasar kaydına bağlayabilirsin. Araç CV'de de hasarla birlikte görünür.", moduleColor("Hasar")));
            gap(content, 9);
        }

        Button add = secondaryButton(photos.size() >= 10 ? "10 fotoğraf sınırına ulaşıldı" : "+ Hasar fotoğrafı ekle (" + photos.size() + "/10)");
        add.setEnabled(photos.size() < 10);
        add.setOnClickListener(v -> pickDamageImages(record.id));
        content.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
    }

    private View damagePhotoThumb(long recordId, AppDatabase.RecordPhoto photo) {
        FrameLayout frame = new FrameLayout(this);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(dp(112), dp(98));
        fp.setMargins(0, 0, dp(9), 0);
        frame.setLayoutParams(fp);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(cardDrawable(surface2, dp(13), stroke));
        try { image.setImageURI(Uri.parse(photo.uri)); } catch (Exception ignored) { }
        image.setOnClickListener(v -> openAttachment(photo.uri));
        frame.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView remove = tv("×", Color.WHITE, 18, true);
        remove.setGravity(Gravity.CENTER);
        remove.setBackground(cardDrawable(Color.argb(220, 28, 35, 35), dp(14), Color.TRANSPARENT));
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(29), dp(29));
        rp.gravity = Gravity.TOP | Gravity.RIGHT;
        rp.setMargins(0, dp(5), dp(5), 0);
        frame.addView(remove, rp);
        remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Fotoğraf silinsin mi?")
                .setMessage("Fotoğraf bu hasar kaydından kaldırılacak.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Sil", (d, w) -> {
                    db.deleteRecordPhoto(photo.id);
                    openRecordDetail(recordId);
                }).show());
        return frame;
    }

    private static class FormRefs {
        EditText title, km, cost, detail, quantity, nextKm, extraText;
        DateInput date, nextDate;
        Spinner subtype, status;
        ArrayList<CheckBox> parts = new ArrayList<>();
        CheckBox fullTank;
        TextView smartStatus;
    }

    private void openRecordForm(String module, AppDatabase.Record existing) {
        currentModule = module;
        currentPage = 1;
        selectedRecordId = existing == null ? -1 : existing.id;
        buildBottomNav();
        LinearLayout content = newContent();
        addBackHeader(content, existing == null ? "Yeni " + moduleTitle(module) : "Kaydı düzenle", moduleTitle(module), () -> {
            if (existing == null) openModule(module);
            else openRecordDetail(existing.id);
        });

        AppDatabase.Vehicle vehicle = db.getVehicle();
        FormRefs f = new FormRefs();
        pendingSmartForm = f;
        pendingSmartModule = module;
        AppDatabase.Record base = existing == null ? new AppDatabase.Record() : existing;
        String initialTitle = existing == null ? defaultTitle(module) : base.title;

        LinearLayout form = card();
        form.setPadding(dp(16), dp(16), dp(16), dp(16));
        form.addView(formSection("Temel bilgiler", "Zorunlu alanları kısa tuttuk"));
        f.title = formField(form, titleHint(module), initialTitle, InputType.TYPE_CLASS_TEXT);
        f.date = formDate(form, "Tarih", existing == null ? today() : base.date, false);
        f.km = formField(form, "Kilometre", existing == null ? String.valueOf(vehicle.km) : String.valueOf(base.km), InputType.TYPE_CLASS_NUMBER);

        if ("Bakım".equals(module)) {
            f.subtype = formSpinner(form, "Bakım türü", new String[]{"Yağ bakımı", "Genel bakım", "Periyodik bakım", "Fren", "Lastik", "Akü", "Klima", "Motor", "Şanzıman", "Elektrik", "Kaporta/Boya", "Diğer"}, base.subtype);
            form.addView(formSection("Değişen parçalar", "Birden fazlasını seçebilirsin"));
            String[] parts = {"Motor yağı", "Yağ filtresi", "Hava filtresi", "Polen filtresi", "Yakıt filtresi", "Fren balatası", "Fren diski", "Buji", "Akü", "Triger", "Lastik", "Antifriz", "Şanzıman yağı", "Diğer"};
            Set<String> selected = new HashSet<>(Arrays.asList(base.extra.split("\\|")));
            for (String p : parts) {
                CheckBox cb = new CheckBox(this);
                cb.setText(p);
                cb.setTextColor(text);
                cb.setTextSize(12);
                cb.setChecked(selected.contains(p));
                f.parts.add(cb);
                form.addView(cb);
            }
            f.cost = formField(form, "Tutar (isteğe bağlı)", moneyRaw(base.cost), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            f.detail = formMultiline(form, "Açıklama / yapılan işlemler", base.detail);
            f.nextDate = formDate(form, "Sonraki bakım tarihi (isteğe bağlı)", base.nextDate, true);
            f.nextKm = formField(form, "Sonraki bakım kilometresi (isteğe bağlı)", base.nextKm > 0 ? String.valueOf(base.nextKm) : "", InputType.TYPE_CLASS_NUMBER);
        } else if ("Hasar".equals(module)) {
            f.subtype = formSpinner(form, "Hasar türü", new String[]{"Çizik / kozmetik", "Göçük", "Kaporta", "Cam", "Kaza", "Mekanik", "Diğer"}, base.subtype);
            f.status = formSpinner(form, "Onarım durumu", new String[]{"Onarılmadı", "Onarıldı", "Kısmi onarım"}, base.status);
            f.cost = formField(form, "Onarım tutarı (isteğe bağlı)", moneyRaw(base.cost), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            f.detail = formMultiline(form, "Hasar ve onarım açıklaması", base.detail);
        } else if ("Ekspertiz".equals(module)) {
            f.subtype = formSpinner(form, "Ekspertiz türü", new String[]{"Genel ekspertiz", "Satış öncesi", "Kaporta/boya", "Mekanik", "Diğer"}, base.subtype);
            f.cost = formField(form, "Ekspertiz tutarı (isteğe bağlı)", moneyRaw(base.cost), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            f.detail = formMultiline(form, "Rapor özeti / bulgular", base.detail);
        } else if ("Muayene".equals(module)) {
            f.subtype = formSpinner(form, "Muayene sonucu", new String[]{"Kusursuz", "Hafif kusurlu", "Ağır kusurlu", "Emniyetsiz"}, base.subtype);
            f.detail = formMultiline(form, "Kusurlar / notlar", base.detail);
            f.nextDate = formDate(form, "Sonraki muayene tarihi", base.nextDate, true);
        } else if ("Vergi".equals(module)) {
            f.subtype = formSpinner(form, "Vergi türü", new String[]{"MTV", "Ek MTV", "Diğer resmî ödeme"}, base.subtype);
            f.status = formSpinner(form, "Durum", new String[]{"Ödendi", "Ödenmedi"}, base.status);
            f.cost = formField(form, "Tutar", moneyRaw(base.cost), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            f.detail = formMultiline(form, "Dönem / açıklama", base.detail);
            f.nextDate = formDate(form, "Son ödeme / sonraki ödeme tarihi", base.nextDate, true);
        } else if ("Sigorta/Kasko".equals(module)) {
            f.subtype = formSpinner(form, "Poliçe türü", new String[]{"Zorunlu trafik sigortası", "Kasko"}, base.subtype);
            f.status = formSpinner(form, "Durum", new String[]{"Aktif", "Sona erdi", "İptal"}, base.status);
            f.cost = formField(form, "Prim / tutar (isteğe bağlı)", moneyRaw(base.cost), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            f.extraText = formField(form, "Poliçe no (isteğe bağlı, CV'de varsayılan gizli)", base.extra, InputType.TYPE_CLASS_TEXT);
            f.detail = formMultiline(form, "Şirket / teminat notları", base.detail);
            f.nextDate = formDate(form, "Bitiş tarihi", base.nextDate, true);
        } else if ("Yakıt".equals(module)) {
            String defaultFuel = existing == null ? fuelRecordDefault(vehicle.fuelType) : base.subtype;
            f.subtype = formSpinner(form, "Yakıt / enerji türü", new String[]{"Benzin", "Dizel", "LPG", "Elektrik", "CNG", "Diğer"}, defaultFuel);
            f.quantity = formField(form, "Kaç litre / kWh?", base.quantity > 0 ? trimDouble(base.quantity) : "", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            f.cost = formField(form, "Toplam ödenen tutar", moneyRaw(base.cost), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            f.fullTank = new CheckBox(this);
            f.fullTank.setText("Depo tamamen dolduruldu");
            f.fullTank.setTextColor(text);
            f.fullTank.setTextSize(12);
            f.fullTank.setChecked("full".equals(base.status));
            form.addView(f.fullTank);
            f.detail = formMultiline(form, "İstasyon / not (isteğe bağlı)", base.detail);
            TextView auto = tv("Birim fiyat, miktar ve toplam tutardan otomatik hesaplanır.", accent, 10, true);
            auto.setPadding(0, dp(4), 0, 0);
            form.addView(auto);
        }
        content.addView(form);
        gap(content, 14);

        Button save = primaryButton(existing == null ? "Kaydı oluştur" : "Değişiklikleri kaydet");
        save.setOnClickListener(v -> saveRecordForm(module, base, existing != null, f));
        content.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
    }

    private void saveRecordForm(String module, AppDatabase.Record r, boolean editing, FormRefs f) {
        String title = f.title.getText().toString().trim();
        String date = f.date.getValue();
        int km = safeInt(f.km.getText().toString(), -1);
        if (title.isEmpty()) { toast("Kayıt başlığı boş olamaz"); return; }
        if (date.isEmpty()) { toast("Tarih seçmelisin"); return; }
        if (km < 0) { toast("Kilometreyi kontrol et"); return; }

        r.type = module;
        r.title = title;
        r.date = date;
        r.km = km;
        r.cost = f.cost == null ? 0 : safeDouble(f.cost.getText().toString());
        r.detail = f.detail == null ? "" : f.detail.getText().toString().trim();
        r.subtype = f.subtype == null ? "" : String.valueOf(f.subtype.getSelectedItem());
        r.status = f.status == null ? "" : String.valueOf(f.status.getSelectedItem());
        r.nextDate = f.nextDate == null ? "" : f.nextDate.getValue();
        r.nextKm = f.nextKm == null ? 0 : safeInt(f.nextKm.getText().toString(), 0);
        r.extra = f.extraText == null ? "" : f.extraText.getText().toString().trim();

        if ("Bakım".equals(module)) {
            ArrayList<String> chosen = new ArrayList<>();
            for (CheckBox cb : f.parts) if (cb.isChecked()) chosen.add(cb.getText().toString());
            r.extra = join(chosen, "|");
        }
        if ("Yakıt".equals(module)) {
            r.quantity = f.quantity == null ? 0 : safeDouble(f.quantity.getText().toString());
            r.unit = "Elektrik".equals(r.subtype) ? "kWh" : "L";
            r.unitPrice = r.quantity > 0 && r.cost > 0 ? r.cost / r.quantity : 0;
            r.status = f.fullTank != null && f.fullTank.isChecked() ? "full" : "partial";
            r.extra = "";
            if (r.quantity <= 0) { toast("Yakıt miktarını girmelisin"); return; }
            if (r.cost <= 0) { toast("Toplam ödenen tutarı girmelisin"); return; }
        }

        AppDatabase.Vehicle currentVehicle = db.getVehicle();
        if (r.km > currentVehicle.km && r.km < 2_000_000) {
            db.updateVehicle(currentVehicle.brand, currentVehicle.model, currentVehicle.year,
                    currentVehicle.plate, r.km, currentVehicle.fuelType);
        }

        if (editing) {
            ReminderScheduler.cancel(this, r.id);
            db.updateRecord(r);
        } else {
            r.id = db.addRecord(r);
        }
        if (!r.nextDate.isEmpty()) ReminderScheduler.schedule(this, r.id, r.title, r.nextDate);
        toast(editing ? "Kayıt güncellendi" : "Kayıt oluşturuldu");
        openRecordDetail(r.id);
    }


    private LinearLayout smartInputPanel(String module, FormRefs f) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(12), dp(11), dp(12), dp(11));
        wrap.setBackground(cardDrawable(accentSoft, dp(16), accent));

        TextView titleView = tv("Akıllı giriş", text, 12, true);
        wrap.addView(titleView);
        TextView subtitle = tv("Fiş, poliçe, muayene/ekspertiz fotoğrafı veya PDF okut; ya da kaydı konuş. Bulunan alanlar forma gelir, otomatik kaydedilmez.", muted, 10, false);
        subtitle.setPadding(0, dp(2), 0, dp(8));
        wrap.addView(subtitle);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button photo = secondaryButton("📷 Fotoğraftan doldur");
        Button voice = secondaryButton("🎤 Sesle doldur");
        actions.addView(photo, new LinearLayout.LayoutParams(0, dp(46), 1f));
        gapHorizontal(actions, 8);
        actions.addView(voice, new LinearLayout.LayoutParams(0, dp(46), 1f));
        wrap.addView(actions);

        f.smartStatus = tv("Henüz akıllı giriş kullanılmadı.", muted, 9, false);
        f.smartStatus.setPadding(0, dp(7), 0, 0);
        wrap.addView(f.smartStatus);

        photo.setOnClickListener(v -> {
            pendingSmartForm = f;
            pendingSmartModule = module;
            showOcrSourceChooser();
        });
        voice.setOnClickListener(v -> {
            pendingSmartForm = f;
            pendingSmartModule = module;
            startSmartVoice();
        });
        return wrap;
    }

    private void showOcrSourceChooser() {
        new AlertDialog.Builder(this)
                .setTitle("Fotoğraftan doldur")
                .setItems(new String[]{"Kamera ile çek", "Galeriden / dosyadan seç"}, (dialog, which) -> {
                    if (which == 0) captureOcrImage();
                    else pickOcrImage();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void pickOcrImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(i, "Fiş / belge fotoğrafı veya PDF seç"), PICK_OCR_IMAGE);
    }

    private void captureOcrImage() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "tasitim_scan_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) { toast("Kamera için geçici dosya oluşturulamadı"); return; }
            Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (camera.resolveActivity(getPackageManager()) == null) {
                getContentResolver().delete(uri, null, null);
                toast("Kamera uygulaması bulunamadı");
                return;
            }
            pendingOcrCameraUri = uri;
            camera.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(camera, CAPTURE_OCR_IMAGE);
        } catch (Exception e) {
            cleanupOcrCameraImage();
            toast("Kamera açılamadı");
        }
    }

    private void startSmartVoice() {
        Intent i = VoiceInput.createIntent();
        if (i.resolveActivity(getPackageManager()) == null) {
            toast("Bu cihazda konuşma tanıma hizmeti bulunamadı");
            return;
        }
        if (assistantInputActive && assistantStatus != null) {
            assistantStatus.setText("Dinliyorum… Kaydı doğal şekilde anlat.");
            assistantStatus.setTextColor(accent);
        } else if (pendingSmartForm != null && pendingSmartForm.smartStatus != null) {
            pendingSmartForm.smartStatus.setText("Dinlemeye hazır… Kaydı doğal şekilde anlat.");
            pendingSmartForm.smartStatus.setTextColor(accent);
        }
        startActivityForResult(i, VoiceInput.REQUEST_CODE);
    }

    private void processOcrImage(Uri uri, boolean temporaryCameraImage) {
        if (uri == null || (!assistantInputActive && (pendingSmartForm == null || pendingSmartModule == null))) {
            if (temporaryCameraImage) cleanupOcrCameraImage();
            return;
        }
        if (assistantInputActive && assistantStatus != null) {
            assistantStatus.setText("Belge cihaz üzerinde okunuyor…");
            assistantStatus.setTextColor(accent);
        } else if (pendingSmartForm != null && pendingSmartForm.smartStatus != null) {
            pendingSmartForm.smartStatus.setText("Belge cihaz üzerinde okunuyor…");
            pendingSmartForm.smartStatus.setTextColor(accent);
        }
        ReceiptOcr.process(this, uri, new ReceiptOcr.Callback() {
            @Override
            public void onResult(RecordParser.Parsed result) {
                runOnUiThread(() -> {
                    if (temporaryCameraImage) cleanupOcrCameraImage();
                    if (assistantInputActive) handleAssistantResult(result, "Fotoğraf");
                    else handleSmartResult(result, "Fotoğraf");
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    if (temporaryCameraImage) cleanupOcrCameraImage();
                    if (assistantInputActive && assistantStatus != null) {
                        assistantInputActive = false;
                        assistantStatus.setText("Fotoğraf okunamadı — daha net çek veya manuel giriş yap.");
                        assistantStatus.setTextColor(warning);
                    } else if (pendingSmartForm != null && pendingSmartForm.smartStatus != null) {
                        pendingSmartForm.smartStatus.setText("Fotoğraf okunamadı — alanları manuel girebilirsin.");
                        pendingSmartForm.smartStatus.setTextColor(warning);
                    }
                    toast("Belge okunamadı");
                });
            }
        });
    }

    private void cleanupOcrCameraImage() {
        Uri uri = pendingOcrCameraUri;
        pendingOcrCameraUri = null;
        if (uri != null) {
            try { getContentResolver().delete(uri, null, null); }
            catch (Exception ignored) { }
        }
    }

    private void handleSmartResult(RecordParser.Parsed parsed, String source) {
        if (parsed == null || pendingSmartForm == null || pendingSmartModule == null) return;
        String detected = parsed.type == null ? "" : parsed.type;
        if (!detected.isEmpty() && !detected.equals(pendingSmartModule)) {
            String current = pendingSmartModule;
            new AlertDialog.Builder(this)
                    .setTitle("Kayıt türü farklı görünüyor")
                    .setMessage("Bu içerik “" + detected + "” kaydı gibi görünüyor. Şu anda “" + current + "” formundasın.")
                    .setNegativeButton("Bu formda kullan", (d, w) -> applyParsedToCurrentForm(parsed, source))
                    .setPositiveButton(detected + " formuna geç", (d, w) -> {
                        openRecordForm(detected, null);
                        applyParsedToCurrentForm(parsed, source);
                    })
                    .show();
            return;
        }
        applyParsedToCurrentForm(parsed, source);
    }

    private void applyParsedToCurrentForm(RecordParser.Parsed p, String source) {
        FormRefs f = pendingSmartForm;
        String module = pendingSmartModule;
        if (f == null || module == null) return;
        int found = 0;

        if (p.date != null && !p.date.isEmpty() && f.date != null) {
            f.date.value.setText(p.date);
            f.date.value.setTextColor(success);
            found++;
        }
        if (p.km > 0 && f.km != null) {
            f.km.setText(String.valueOf(p.km));
            markSmartField(f.km);
            found++;
        }
        if (p.amount > 0 && f.cost != null) {
            f.cost.setText(trimDouble(p.amount));
            markSmartField(f.cost);
            found++;
        }
        if (p.quantity > 0 && f.quantity != null) {
            f.quantity.setText(trimDouble(p.quantity));
            markSmartField(f.quantity);
            found++;
        }
        if (p.nextDate != null && !p.nextDate.isEmpty() && f.nextDate != null) {
            f.nextDate.value.setText(p.nextDate);
            f.nextDate.value.setTextColor(success);
            found++;
        }
        if (p.policyNo != null && !p.policyNo.isEmpty() && f.extraText != null) {
            f.extraText.setText(p.policyNo);
            markSmartField(f.extraText);
            found++;
        }

        if ("Yakıt".equals(module)) {
            if (p.fuelType != null && !p.fuelType.isEmpty() && f.subtype != null) {
                selectSpinnerValue(f.subtype, p.fuelType);
                markSmartSpinner(f.subtype);
                found++;
            }
            if (p.vendor != null && !p.vendor.isEmpty()) {
                if (f.title != null) {
                    f.title.setText(p.vendor + " yakıt alımı");
                    markSmartField(f.title);
                }
                if (f.detail != null) {
                    f.detail.setText(p.vendor);
                    markSmartField(f.detail);
                }
                found++;
            }
        } else if ("Bakım".equals(module)) {
            if (p.maintenanceSubtype != null && !p.maintenanceSubtype.isEmpty() && f.subtype != null) {
                selectSpinnerValue(f.subtype, p.maintenanceSubtype);
                markSmartSpinner(f.subtype);
                found++;
            }
            if (!p.maintenanceParts.isEmpty()) {
                for (CheckBox cb : f.parts) {
                    if (p.maintenanceParts.contains(String.valueOf(cb.getText()))) cb.setChecked(true);
                }
                found++;
            }
            if (p.vendor != null && !p.vendor.isEmpty()) {
                if (f.title != null) { f.title.setText(p.vendor + " bakım"); markSmartField(f.title); }
                if (f.detail != null && f.detail.getText().toString().trim().isEmpty()) {
                    f.detail.setText("Servis: " + p.vendor);
                    markSmartField(f.detail);
                }
                found++;
            }
            if (p.detailSummary != null && !p.detailSummary.isEmpty() && f.detail != null) {
                f.detail.setText(p.detailSummary);
                markSmartField(f.detail);
                found++;
            }
        } else if ("Sigorta/Kasko".equals(module)) {
            if (p.insuranceSubtype != null && !p.insuranceSubtype.isEmpty() && f.subtype != null) {
                selectSpinnerValue(f.subtype, p.insuranceSubtype);
                markSmartSpinner(f.subtype);
                found++;
            }
            if (f.detail != null) {
                String insuranceDetail = (p.detailSummary != null && !p.detailSummary.isEmpty()) ? p.detailSummary : p.vendor;
                if (insuranceDetail != null && !insuranceDetail.isEmpty()) {
                    f.detail.setText(insuranceDetail);
                    markSmartField(f.detail);
                    found++;
                }
            }
        } else if ("Muayene".equals(module)) {
            if (p.inspectionResult != null && !p.inspectionResult.isEmpty() && f.subtype != null) {
                selectSpinnerValue(f.subtype, p.inspectionResult);
                markSmartSpinner(f.subtype);
                found++;
            }
            if (p.detailSummary != null && !p.detailSummary.isEmpty() && f.detail != null) {
                f.detail.setText(p.detailSummary);
                markSmartField(f.detail);
                found++;
            }
        } else if ("Ekspertiz".equals(module)) {
            if (p.expertiseSubtype != null && !p.expertiseSubtype.isEmpty() && f.subtype != null) {
                selectSpinnerValue(f.subtype, p.expertiseSubtype);
                markSmartSpinner(f.subtype);
                found++;
            }
            if (p.detailSummary != null && !p.detailSummary.isEmpty() && f.detail != null) {
                f.detail.setText(p.detailSummary);
                markSmartField(f.detail);
                found++;
            }
        } else if ("Vergi".equals(module)) {
            if (f.subtype != null) {
                String taxType = (p.taxSubtype != null && !p.taxSubtype.isEmpty()) ? p.taxSubtype : "MTV";
                selectSpinnerValue(f.subtype, taxType);
                markSmartSpinner(f.subtype);
                found++;
            }
            if (p.detailSummary != null && !p.detailSummary.isEmpty() && f.detail != null) {
                f.detail.setText(p.detailSummary);
                markSmartField(f.detail);
                found++;
            }
        } else if ("Hasar".equals(module) && p.type != null && "Hasar".equals(p.type)) {
            if (f.subtype != null) {
                selectSpinnerValue(f.subtype, "Kaza");
                markSmartSpinner(f.subtype);
                found++;
            }
            if (p.detailSummary != null && !p.detailSummary.isEmpty() && f.detail != null) {
                f.detail.setText(p.detailSummary);
                markSmartField(f.detail);
                found++;
            }
        }

        if (p.vendor != null && !p.vendor.isEmpty() && f.title != null &&
                !"Yakıt".equals(module) && !"Bakım".equals(module) &&
                f.title.getText().toString().trim().equals(defaultTitle(module))) {
            f.title.setText(p.vendor + " - " + moduleTitle(module));
            markSmartField(f.title);
        }

        if (f.smartStatus != null) {
            f.smartStatus.setText(smartResultSummary(module, p, source));
            f.smartStatus.setTextColor(found > 0 ? success : warning);
        }
        if (found > 0) toast(source + " verileri forma aktarıldı — kaydetmeden önce kontrol et");
        else toast("Uygun alan okunamadı — manuel girebilirsin");
    }

    private String smartResultSummary(String module, RecordParser.Parsed p, String source) {
        ArrayList<String> items = new ArrayList<>();
        items.add("Tarih " + ((p.date != null && !p.date.isEmpty()) ? "✓" : "okunamadı"));
        items.add("KM " + (p.km > 0 ? "✓" : "okunamadı"));
        if (!"Muayene".equals(module)) items.add("Tutar " + (p.amount > 0 ? "✓" : "okunamadı"));
        if ("Yakıt".equals(module)) {
            items.add("Miktar " + (p.quantity > 0 ? "✓" : "okunamadı"));
            items.add("İstasyon " + ((p.vendor != null && !p.vendor.isEmpty()) ? "✓" : "okunamadı"));
        } else if ("Bakım".equals(module) || "Sigorta/Kasko".equals(module)) {
            items.add("Firma " + ((p.vendor != null && !p.vendor.isEmpty()) ? "✓" : "okunamadı"));
        }
        if ("Sigorta/Kasko".equals(module)) {
            items.add("Poliçe no " + ((p.policyNo != null && !p.policyNo.isEmpty()) ? "✓" : "okunamadı"));
            items.add("Bitiş " + ((p.nextDate != null && !p.nextDate.isEmpty()) ? "✓" : "okunamadı"));
        } else if ("Muayene".equals(module)) {
            items.add("Sonuç " + ((p.inspectionResult != null && !p.inspectionResult.isEmpty()) ? "✓" : "okunamadı"));
            items.add("Geçerlilik " + ((p.nextDate != null && !p.nextDate.isEmpty()) ? "✓" : "okunamadı"));
        } else if ("Ekspertiz".equals(module)) {
            items.add("Rapor özeti " + ((p.detailSummary != null && !p.detailSummary.isEmpty()) ? "✓" : "okunamadı"));
        }
        if (p.confidence > 0) items.add("Belge güveni %" + p.confidence);
        return source + " sonucu • " + join(items, " • ") + "\nOkunamayan alanları manuel gir; otomatik kayıt yapılmadı.";
    }

    private void markSmartField(EditText field) {
        if (field == null) return;
        field.setBackground(cardDrawable(accentSoft, dp(14), accent));
    }

    private void markSmartSpinner(Spinner spinner) {
        if (spinner == null) return;
        spinner.setBackground(cardDrawable(accentSoft, dp(14), accent));
    }

    private void confirmDeleteRecord(AppDatabase.Record r) {
        new AlertDialog.Builder(this)
                .setTitle("Kaydı sil?")
                .setMessage("Bu işlem geri alınamaz. " + r.title + " kaydı silinecek.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Sil", (d, w) -> {
                    ReminderScheduler.cancel(this, r.id);
                    db.deleteRecord(r.id);
                    toast("Kayıt silindi");
                    openModule(currentModule);
                }).show();
    }

    private void renderExpenses(LinearLayout content) {
        addBackHeader(content, "Giderler", "Araçla ilgili diğer harcamalar", () -> renderPage(1));
        LinearLayout summary = card();
        summary.setPadding(dp(17), dp(16), dp(17), dp(16));
        summary.addView(tv("TOPLAM KAYITLI GİDER", muted, 10, true));
        summary.addView(tv(formatMoney(db.getExpenseTotal()), text, 27, true));
        content.addView(summary);
        gap(content, 12);
        Button add = compactPrimary("+ Gider ekle");
        add.setOnClickListener(v -> openExpenseForm(null));
        content.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        gap(content, 16);
        for (AppDatabase.Expense e : db.getExpenses()) content.addView(expenseCard(e));
    }

    private View expenseCard(AppDatabase.Expense e) {
        LinearLayout c = card();
        c.setPadding(dp(15), dp(14), dp(15), dp(14));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(tv(e.category, text, 15, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(tv(formatMoney(e.amount), accent, 15, true));
        c.addView(top);
        c.addView(tv(e.date + (e.note.isEmpty() ? "" : "  •  " + e.note), muted, 11, false));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(9), 0, 0);
        TextView edit = pill("Düzenle", accent, accentSoft);
        edit.setOnClickListener(v -> openExpenseForm(e));
        actions.addView(edit);
        gapHorizontal(actions, 8);
        TextView del = pill("Sil", danger, surface2);
        del.setOnClickListener(v -> confirmDeleteExpense(e));
        actions.addView(del);
        c.addView(actions);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,dp(9));
        c.setLayoutParams(lp);
        return c;
    }

    private void openExpenseForm(AppDatabase.Expense existing) {
        currentModule = "Giderler";
        currentPage = 1;
        buildBottomNav();
        LinearLayout content = newContent();
        addBackHeader(content, existing == null ? "Yeni gider" : "Gideri düzenle", "Harcamayı kaydet", () -> openModule("Giderler"));
        LinearLayout form = card();
        form.setPadding(dp(16),dp(16),dp(16),dp(16));
        Spinner category = formSpinner(form, "Kategori", new String[]{"Otopark", "Otoyol / geçiş", "Yıkama", "Aksesuar", "Ceza", "Diğer"}, existing == null ? "" : existing.category);
        DateInput date = formDate(form, "Tarih", existing == null ? today() : existing.date, false);
        EditText amount = formField(form, "Tutar", existing == null ? "" : moneyRaw(existing.amount), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText note = formMultiline(form, "Not (isteğe bağlı)", existing == null ? "" : existing.note);
        content.addView(form);
        gap(content,14);
        Button save = primaryButton(existing == null ? "Gideri kaydet" : "Değişiklikleri kaydet");
        save.setOnClickListener(v -> {
            double a = safeDouble(amount.getText().toString());
            if (a <= 0) { toast("Tutarı girmelisin"); return; }
            if (existing == null) db.addExpense(String.valueOf(category.getSelectedItem()), date.getValue(), a, note.getText().toString().trim());
            else {
                existing.category = String.valueOf(category.getSelectedItem());
                existing.date = date.getValue();
                existing.amount = a;
                existing.note = note.getText().toString().trim();
                db.updateExpense(existing);
            }
            toast("Gider kaydedildi");
            openModule("Giderler");
        });
        content.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
    }

    private void confirmDeleteExpense(AppDatabase.Expense e) {
        new AlertDialog.Builder(this)
                .setTitle("Gideri sil?")
                .setMessage("Bu gider kaydı kalıcı olarak silinecek.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Sil", (d,w) -> {
                    db.deleteExpense(e.id);
                    openModule("Giderler");
                }).show();
    }

    private void renderGallery(LinearLayout content) {
        addHeader(content, "Galeri", "Aracının fotoğraf hikâyesi");
        Button add = compactPrimary("+ Fotoğraf ekle");
        add.setOnClickListener(v -> pickGalleryImage());
        content.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        gap(content, 14);
        List<AppDatabase.Photo> photos = db.getPhotos();
        if (photos.isEmpty()) {
            content.addView(infoCard("Galeri boş", "Araç fotoğraflarını burada saklayabilirsin. Hasar belgeleri kendi kayıtlarında kalır.", accent));
            return;
        }
        for (AppDatabase.Photo p : photos) {
            LinearLayout c = card();
            c.setPadding(dp(7),dp(7),dp(7),dp(10));
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try { image.setImageURI(Uri.parse(p.uri)); } catch(Exception ignored) {}
            c.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)));
            LinearLayout footer = new LinearLayout(this);
            footer.setOrientation(LinearLayout.HORIZONTAL);
            footer.setGravity(Gravity.CENTER_VERTICAL);
            footer.setPadding(dp(9),dp(8),dp(9),0);
            footer.addView(tv(p.date, muted, 10, false), new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            TextView del = pill("Sil", danger, surface2);
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Fotoğraf silinsin mi?")
                    .setNegativeButton("Vazgeç",null)
                    .setPositiveButton("Sil",(d,w)->{ db.deletePhoto(p.id); renderPage(3); })
                    .show());
            footer.addView(del);
            c.addView(footer);
            content.addView(c);
            gap(content,10);
        }
    }

private void renderCv(LinearLayout content) {
        AppDatabase.Vehicle v = db.getVehicle();
        if (v.brand == null || v.brand.trim().isEmpty() || v.model == null || v.model.trim().isEmpty()) {
            addHeader(content, "Araç CV", "Önce araç bilgilerini ekle");
            content.addView(infoCard("CV için araç bilgisi gerekli", "Araç profili boş. Marka, model, yıl ve kilometreyi ekledikten sonra CV oluşturabilirsin.", accent));
            gap(content, 10);
            Button add = primaryButton("Araç bilgilerini ekle");
            add.setOnClickListener(x -> openVehicleForm());
            content.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
            return;
        }
        String cvVariant = prefs.getString("vehicle_catalog_variant", "").trim();
        if (cvVariant.isEmpty()) {
            cvVariant = (prefs.getString("vehicle_engine", "") + " " + prefs.getString("vehicle_trim", "")).trim();
        }
        String fullCvName = (v.year + " " + v.brand + " " + v.model + (cvVariant.isEmpty() ? "" : " " + cvVariant)).replaceAll("\\s+", " ").trim();

        TextView title = tv("Araç CV", text, 22, true);
        content.addView(title);
        TextView subtitle = tv(fullCvName, muted, 10, false);
        subtitle.setPadding(0,dp(2),0,dp(9));
        content.addView(subtitle);

        LinearLayout summary = card();
        summary.setPadding(dp(13),dp(11),dp(13),dp(11));
        summary.addView(tv(fullCvName, text, 16, true));
        String meta = (prefs.getString("vehicle_body", "") + "  •  " + v.fuelType + "  •  " + prefs.getString("vehicle_transmission", "")).trim();
        meta = meta.replaceFirst("^\\s*•\\s*", "").replaceFirst("\\s*•\\s*$", "");
        if (!meta.trim().isEmpty()) summary.addView(tv(meta, muted, 9, false));
        content.addView(summary);
        gap(content,10);

        sectionTitle(content, "Araç resimleri", "Ana araç fotoğrafına ek olarak en fazla 10 fotoğraf");
        LinearLayout photoCard = card();
        photoCard.setPadding(dp(12),dp(11),dp(12),dp(11));
        cvPhotoCountView = tv(cvPhotoUris.size() + " / " + VehicleCvPdf.MAX_EXTRA_PHOTOS + " araç resmi seçildi", text, 12, true);
        photoCard.addView(cvPhotoCountView);
        photoCard.addView(tv("Seçilen araç resimleri yalnız oluşturulan PDF içinde kullanılır.", muted, 9, false));
        gap(photoCard,8);

        if (!cvPhotoUris.isEmpty()) {
            android.widget.HorizontalScrollView scrollPhotos = new android.widget.HorizontalScrollView(this);
            scrollPhotos.setHorizontalScrollBarEnabled(false);
            LinearLayout thumbs = new LinearLayout(this);
            thumbs.setOrientation(LinearLayout.HORIZONTAL);
            for (Uri uri : new ArrayList<>(cvPhotoUris)) thumbs.addView(cvPhotoThumb(uri));
            scrollPhotos.addView(thumbs);
            photoCard.addView(scrollPhotos,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(88)));
        }

        LinearLayout photoActions = new LinearLayout(this);
        photoActions.setOrientation(LinearLayout.HORIZONTAL);
        Button choose = secondaryButton(cvPhotoUris.isEmpty() ? "Fotoğraf seç" : "+ Fotoğraf ekle");
        choose.setOnClickListener(vw -> pickCvImages());
        photoActions.addView(choose, new LinearLayout.LayoutParams(0,dp(41),1f));
        gapHorizontal(photoActions,7);
        Button clear = secondaryButton("Temizle");
        clear.setEnabled(!cvPhotoUris.isEmpty());
        clear.setAlpha(cvPhotoUris.isEmpty() ? 0.45f : 1f);
        clear.setOnClickListener(vw -> { cvPhotoUris.clear(); renderPage(3); });
        photoActions.addView(clear, new LinearLayout.LayoutParams(0,dp(41),1f));
        photoCard.addView(photoActions);
        content.addView(photoCard);
        gap(content,13);

        sectionTitle(content, "PDF seçenekleri", "İsteğe bağlı bilgileri seç");
        LinearLayout options = card();
        options.setPadding(dp(12),dp(10),dp(12),dp(11));

        options.addView(tv("CV'ye eklenecek bölümler", text, 12, true));
        TextView sectionHelp = tv("İstemediğin bölümü kapat; PDF'ye hiç eklenmez.", muted, 9, false);
        sectionHelp.setPadding(0, dp(2), 0, dp(5));
        options.addView(sectionHelp);
        CheckBox cvMaintenance = cvSectionOption(options, "Bakım & Onarım", "cv_include_maintenance", true);
        CheckBox cvInspection = cvSectionOption(options, "Muayene", "cv_include_inspection", true);
        CheckBox cvDamage = cvSectionOption(options, "Hasar geçmişi ve hasar fotoğrafları", "cv_include_damage", true);
        CheckBox cvExpertise = cvSectionOption(options, "Ekspertiz", "cv_include_expertise", true);
        CheckBox cvInsurance = cvSectionOption(options, "Sigorta & Kasko", "cv_include_insurance", true);
        CheckBox cvTax = cvSectionOption(options, "Vergi / resmî ödemeler", "cv_include_tax", true);
        CheckBox cvFuel = cvSectionOption(options, "Yakıt / enerji geçmişi", "cv_include_fuel", false);
        CheckBox cvExpenses = cvSectionOption(options, "Diğer giderler", "cv_include_expenses", false);
        CheckBox cvPhotos = cvSectionOption(options, "Seçtiğim araç fotoğrafları", "cv_include_photos", true);
        gap(options, 6);

        CheckBox showCosts = new CheckBox(this);
        showCosts.setText("Maliyetleri göster");
        showCosts.setTextColor(text);
        showCosts.setTextSize(11);
        showCosts.setChecked(prefs.getBoolean("cv_show_costs", false));
        options.addView(showCosts);

        CheckBox showPrice = new CheckBox(this);
        showPrice.setText("Satış fiyatını CV'ye ekle");
        showPrice.setTextColor(text);
        showPrice.setTextSize(11);
        showPrice.setChecked(prefs.getBoolean("cv_show_price", false));
        options.addView(showPrice);

        EditText salePrice = formField(options, "İstenen satış fiyatı (₺)", prefs.getString("cv_sale_price", ""), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        salePrice.setEnabled(showPrice.isChecked());
        salePrice.setAlpha(showPrice.isChecked() ? 1f : 0.45f);
        showPrice.setOnCheckedChangeListener((buttonView, isChecked) -> {
            salePrice.setEnabled(isChecked);
            salePrice.setAlpha(isChecked ? 1f : 0.45f);
        });

        EditText phone = formField(options, "Telefon (isteğe bağlı)", prefs.getString("cv_phone", ""), InputType.TYPE_CLASS_PHONE);
        EditText note = formMultiline(options, "Açıklama (isteğe bağlı)", prefs.getString("cv_note", ""));
        content.addView(options);
        gap(content,12);

        Button create = primaryButton("PDF oluştur");
        create.setOnClickListener(vw -> {
            if (showPrice.isChecked() && salePrice.getText().toString().trim().isEmpty()) {
                toast("Satış fiyatını gir veya fiyat seçeneğini kapat");
                return;
            }
            prefs.edit()
                    .putBoolean("cv_show_plate", false)
                    .putBoolean("cv_include_maintenance", cvMaintenance.isChecked())
                    .putBoolean("cv_include_inspection", cvInspection.isChecked())
                    .putBoolean("cv_include_damage", cvDamage.isChecked())
                    .putBoolean("cv_include_expertise", cvExpertise.isChecked())
                    .putBoolean("cv_include_insurance", cvInsurance.isChecked())
                    .putBoolean("cv_include_tax", cvTax.isChecked())
                    .putBoolean("cv_include_fuel", cvFuel.isChecked())
                    .putBoolean("cv_include_expenses", cvExpenses.isChecked())
                    .putBoolean("cv_include_photos", cvPhotos.isChecked())
                    .putBoolean("cv_show_costs", showCosts.isChecked())
                    .putBoolean("cv_show_price", showPrice.isChecked())
                    .putString("cv_sale_price", salePrice.getText().toString().trim())
                    .putString("cv_phone", phone.getText().toString().trim())
                    .putString("cv_note", note.getText().toString().trim())
                    .apply();
            createVehiclePdf();
        });
        content.addView(create, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
    }

    private CheckBox cvSectionOption(LinearLayout parent, String label, String prefKey, boolean defaultValue) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(text);
        box.setTextSize(11);
        box.setChecked(prefs.getBoolean(prefKey, defaultValue));
        parent.addView(box);
        return box;
    }

    private void renderSettings(LinearLayout content) {
        addHeader(content, "Ayarlar", "Sade, küçük ve işe yarayan ayarlar");
        sectionTitle(content, "Görünüm", "Tema seçimi");
        LinearLayout theme = card();
        theme.setPadding(dp(10),dp(10),dp(10),dp(10));
        LinearLayout segmented = new LinearLayout(this);
        segmented.setOrientation(LinearLayout.HORIZONTAL);
        String selected = prefs.getString("theme_mode","system");
        addThemeSegment(segmented,"Sistem","system",selected);
        addThemeSegment(segmented,"Açık","light",selected);
        addThemeSegment(segmented,"Koyu","dark",selected);
        theme.addView(segmented, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        content.addView(theme);
        gap(content,18);

        sectionTitle(content, "Bildirimler", "Hatırlatmaların çalıştığını kontrol et");
        LinearLayout notifications = card();
        notifications.setPadding(dp(15),dp(14),dp(15),dp(14));
        boolean enabled = notificationsEnabled();
        notifications.addView(tv(enabled ? "Bildirimler açık" : "Bildirim izni kapalı", enabled ? success : danger, 14, true));
        notifications.addView(tv("Bakım, muayene, vergi ve sigorta tarihleri için 7 gün ve 1 gün kala hatırlatır.", muted, 10, false));
        gap(notifications,10);
        Button test = secondaryButton("Test bildirimi gönder");
        test.setOnClickListener(v -> {
            if (!notificationsEnabled()) requestNotificationPermissionIfNeeded();
            else ReminderScheduler.test(this);
        });
        notifications.addView(test,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(44)));
        content.addView(notifications);
        gap(content,18);

        sectionTitle(content, "Gizlilik", "Toplamadığımız veri en güvenli veridir");
        content.addView(infoCard("Şase/VIN yok", "Uygulama şase numarası istemez, saklamaz veya CV'ye yazmaz.", accent));
    }

    private void addThemeSegment(LinearLayout parent, String label, String value, String selected) {
        boolean active = value.equals(selected);
        TextView t = tv(label, active ? (dark ? Color.WHITE : text) : muted, 11, active);
        t.setGravity(Gravity.CENTER);
        t.setBackground(cardDrawable(active ? accentSoft : Color.TRANSPARENT, dp(13), Color.TRANSPARENT));
        t.setOnClickListener(v -> {
            prefs.edit().putString("theme_mode", value).apply();
            recreate();
        });
        parent.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

private void openVehicleForm() {
        currentPage = 0;
        currentModule = "Araç";
        selectedRecordId = -1;
        buildBottomNav();
        LinearLayout content = newContent();
        addBackHeader(content,"Araç bilgileri","Türkiye kataloğundan seç; teknik bilgiler otomatik dolsun",()->renderPage(0));
        AppDatabase.Vehicle v = db.getVehicle();

        String savedType = prefs.getString("vehicle_type", TurkeyVehicleCatalog.TYPE_CAR);
        LinearLayout form = card();
        form.setPadding(dp(16),dp(16),dp(16),dp(16));
        form.addView(formSection("Araç seçimi", "Araç türü → marka → model → yıl → motor / paket"));

        Spinner type = formSpinner(form,"Araç türü",TurkeyVehicleCatalog.vehicleTypes(),savedType);
        Spinner brand = formSpinner(form,"Marka",TurkeyVehicleCatalog.brandsForType(savedType),v.brand);
        Spinner model = formSpinner(form,"Model",TurkeyVehicleCatalog.modelsFor(savedType,v.brand),v.model);
        Spinner year = formSpinner(form,"Model yılı",TurkeyVehicleCatalog.years(),String.valueOf(v.year));
        String savedVariant = prefs.getString("vehicle_catalog_variant", "");
        Spinner variant = formSpinner(form,"Motor / paket / versiyon",
                TurkeyVehicleSpecs.variantLabels(v.brand, v.model, v.year), savedVariant);

        EditText manualBrand = formField(form,"Manuel marka (yalnız listede yoksa)","Diğer / Manuel".equals(String.valueOf(brand.getSelectedItem())) ? v.brand : "",InputType.TYPE_CLASS_TEXT);
        EditText manualModel = formField(form,"Manuel model (yalnız listede yoksa)","Listede yok / Manuel".equals(String.valueOf(model.getSelectedItem())) ? v.model : "",InputType.TYPE_CLASS_TEXT);

        form.addView(formSection("Otomatik teknik bilgiler", "Eşleşen varyant seçildiğinde aşağıdaki alanlar katalogdan doldurulur"));
        Spinner body = formSpinner(form,"Kasa tipi",TurkeyVehicleCatalog.bodyTypesFor(savedType),prefs.getString("vehicle_body", ""));
        Spinner fuel = formSpinner(form,"Yakıt / güç tipi",TurkeyVehicleCatalog.fuelTypes(),v.fuelType);
        Spinner transmission = formSpinner(form,"Şanzıman",TurkeyVehicleCatalog.transmissions(),prefs.getString("vehicle_transmission", ""));
        EditText generation = formField(form,"Nesil / seri",prefs.getString("vehicle_generation", ""),InputType.TYPE_CLASS_TEXT);
        EditText trim = formField(form,"Paket / versiyon",prefs.getString("vehicle_trim", ""),InputType.TYPE_CLASS_TEXT);
        EditText engine = formField(form,"Motor",prefs.getString("vehicle_engine", ""),InputType.TYPE_CLASS_TEXT);
        EditText power = formField(form,"Motor gücü",prefs.getString("vehicle_power", ""),InputType.TYPE_CLASS_TEXT);
        EditText drivetrain = formField(form,"Çekiş",prefs.getString("vehicle_drivetrain", ""),InputType.TYPE_CLASS_TEXT);
        TextView catalogState = tv("Model ve yılı seçtiğinde uygun motor/paket seçenekleri otomatik yüklenir.", accent, 10, true);
        catalogState.setPadding(dp(2),dp(2),dp(2),dp(12));
        form.addView(catalogState);

        form.addView(formSection("Sana özel bilgiler", "Bunlar katalogdan gelmez"));
        EditText color = formField(form,"Renk (isteğe bağlı)",prefs.getString("vehicle_color", ""),InputType.TYPE_CLASS_TEXT);
        EditText km = formField(form,"Güncel kilometre",String.valueOf(v.km),InputType.TYPE_CLASS_NUMBER);

        final boolean[] updating = {false};

        Runnable refreshVariant = () -> {
            if (updating[0]) return;
            updating[0] = true;
            String b = String.valueOf(brand.getSelectedItem());
            String m = String.valueOf(model.getSelectedItem());
            int y = safeInt(String.valueOf(year.getSelectedItem()), v.year);
            updateVariantChoices(variant, b, m, y, "");
            applySelectedVehicleSpec(type, brand, model, year, variant, body, fuel, transmission,
                    generation, trim, engine, power, drivetrain, catalogState);
            updating[0] = false;
        };

        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (updating[0]) return;
                updating[0] = true;
                String selectedType = String.valueOf(type.getSelectedItem());
                String preferredBrand = first ? v.brand : "";
                first = false;
                setSpinnerItems(brand, TurkeyVehicleCatalog.brandsForType(selectedType), preferredBrand);
                String selectedBrand = String.valueOf(brand.getSelectedItem());
                setSpinnerItems(model, TurkeyVehicleCatalog.modelsFor(selectedType, selectedBrand), "");
                setSpinnerItems(body, TurkeyVehicleCatalog.bodyTypesFor(selectedType), "");
                String selectedModel = String.valueOf(model.getSelectedItem());
                int selectedYear = safeInt(String.valueOf(year.getSelectedItem()), v.year);
                updateVariantChoices(variant, selectedBrand, selectedModel, selectedYear, "");
                applySelectedVehicleSpec(type, brand, model, year, variant, body, fuel, transmission,
                        generation, trim, engine, power, drivetrain, catalogState);
                updating[0] = false;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        brand.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (updating[0]) return;
                updating[0] = true;
                String selectedType = String.valueOf(type.getSelectedItem());
                String selectedBrand = String.valueOf(brand.getSelectedItem());
                String preferred = first ? v.model : "";
                first = false;
                setSpinnerItems(model, TurkeyVehicleCatalog.modelsFor(selectedType, selectedBrand), preferred);
                String selectedModel = String.valueOf(model.getSelectedItem());
                int selectedYear = safeInt(String.valueOf(year.getSelectedItem()), v.year);
                updateVariantChoices(variant, selectedBrand, selectedModel, selectedYear, "");
                applySelectedVehicleSpec(type, brand, model, year, variant, body, fuel, transmission,
                        generation, trim, engine, power, drivetrain, catalogState);
                updating[0] = false;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        model.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                refreshVariant.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        year.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                refreshVariant.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        variant.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (updating[0]) return;
                applySelectedVehicleSpec(type, brand, model, year, variant, body, fuel, transmission,
                        generation, trim, engine, power, drivetrain, catalogState);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        // İlk açılışta kayıtlı varyantı koru ve teknik alanları otomatik doldur.
        updateVariantChoices(variant, v.brand, v.model, v.year, savedVariant);
        applySelectedVehicleSpec(type, brand, model, year, variant, body, fuel, transmission,
                generation, trim, engine, power, drivetrain, catalogState);

        content.addView(form);
        gap(content,14);
        Button save = primaryButton("Araç bilgilerini kaydet");
        save.setOnClickListener(x -> {
            String selectedType = String.valueOf(type.getSelectedItem());
            String selectedBrand = String.valueOf(brand.getSelectedItem());
            String selectedModel = String.valueOf(model.getSelectedItem());
            if ("Diğer / Manuel".equals(selectedBrand)) selectedBrand = manualBrand.getText().toString().trim();
            if ("Listede yok / Manuel".equals(selectedModel)) selectedModel = manualModel.getText().toString().trim();
            if(selectedBrand.isEmpty() || selectedModel.isEmpty()) {
                toast("Marka ve model gerekli");
                return;
            }
            int selectedYear = safeInt(String.valueOf(year.getSelectedItem()), v.year);
            db.updateVehicle(
                    selectedBrand,
                    selectedModel,
                    selectedYear,
                    "",
                    safeInt(km.getText().toString(),v.km),
                    String.valueOf(fuel.getSelectedItem())
            );
            prefs.edit()
                    .putString("vehicle_type", selectedType)
                    .putString("vehicle_catalog_variant", String.valueOf(variant.getSelectedItem()))
                    .putString("vehicle_body", String.valueOf(body.getSelectedItem()))
                    .putString("vehicle_transmission", String.valueOf(transmission.getSelectedItem()))
                    .putString("vehicle_generation", generation.getText().toString().trim())
                    .putString("vehicle_trim", trim.getText().toString().trim())
                    .putString("vehicle_engine", engine.getText().toString().trim())
                    .putString("vehicle_power", power.getText().toString().trim())
                    .putString("vehicle_drivetrain", drivetrain.getText().toString().trim())
                    .putString("vehicle_color", color.getText().toString().trim())
                    .apply();
            toast("Araç profili ve teknik bilgiler kaydedildi");
            renderPage(0);
        });
        content.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));
    }

    private static class DateInput {
        TextView value;
        String getValue() { return value.getText().toString().trim(); }
    }

    private DateInput formDate(LinearLayout parent, String label, String value, boolean optional) {
        parent.addView(formLabel(label));
        DateInput input = new DateInput();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(13),0,dp(10),0);
        box.setBackground(inputDrawable());
        input.value = tv(value == null ? "" : value, (value == null || value.isEmpty()) ? muted : text, 13, false);
        input.value.setHint(optional ? "Tarih seç (isteğe bağlı)" : "Tarih seç");
        box.addView(input.value,new LinearLayout.LayoutParams(0,dp(50),1f));
        TextView icon = tv("▣",accent,15,true);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon,new LinearLayout.LayoutParams(dp(34),dp(50)));
        box.setOnClickListener(v -> showDatePicker(input.value));
        if(optional) {
            icon.setOnLongClickListener(v -> {
                input.value.setText("");
                input.value.setTextColor(muted);
                return true;
            });
        }
        parent.addView(box,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
        gap(parent,10);
        return input;
    }

    private void showDatePicker(TextView target) {
        Calendar c = Calendar.getInstance();
        try {
            Date d = new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).parse(target.getText().toString());
            if(d != null) c.setTime(d);
        } catch(Exception ignored) {}
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                dark ? AlertDialog.THEME_DEVICE_DEFAULT_DARK : AlertDialog.THEME_DEVICE_DEFAULT_LIGHT,
                (view, year, month, day) -> {
                    target.setText(String.format(Locale.getDefault(),"%02d.%02d.%04d",day,month+1,year));
                    target.setTextColor(text);
                },
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private EditText formField(LinearLayout parent,String label,String value,int inputType) {
        parent.addView(formLabel(label));
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextColor(text);
        e.setHintTextColor(muted);
        e.setTextSize(13);
        e.setSingleLine(true);
        e.setInputType(inputType);
        e.setPadding(dp(13),0,dp(13),0);
        e.setBackground(inputDrawable());
        parent.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
        gap(parent,10);
        return e;
    }

    private EditText formMultiline(LinearLayout parent,String label,String value) {
        parent.addView(formLabel(label));
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextColor(text);
        e.setHintTextColor(muted);
        e.setTextSize(13);
        e.setGravity(Gravity.TOP);
        e.setMinLines(3);
        e.setPadding(dp(13),dp(12),dp(13),dp(12));
        e.setBackground(inputDrawable());
        parent.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(92)));
        gap(parent,10);
        return e;
    }

    private Spinner formSpinner(LinearLayout parent,String label,String[] values,String selected) {
        parent.addView(formLabel(label));
        Spinner s = new Spinner(this);
        s.setBackground(inputDrawable());
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,values) {
            @Override
            public View getView(int position,View convertView,ViewGroup parent) {
                TextView t = (TextView)super.getView(position,convertView,parent);
                t.setTextColor(text);
                t.setTextSize(13);
                t.setPadding(dp(13),0,dp(13),0);
                return t;
            }
            @Override
            public View getDropDownView(int position,View convertView,ViewGroup parent) {
                TextView t = (TextView)super.getDropDownView(position,convertView,parent);
                t.setTextColor(Color.rgb(25,30,30));
                t.setTextSize(14);
                t.setPadding(dp(16),dp(12),dp(16),dp(12));
                return t;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        if(selected != null && !selected.isEmpty()) {
            for(int i=0;i<values.length;i++) {
                if(values[i].equals(selected)) {
                    s.setSelection(i);
                    break;
                }
            }
        }
        parent.addView(s,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
        gap(parent,10);
        return s;
    }


    private void setSpinnerItems(Spinner spinner, String[] values, String selected) {
        ArrayList<String> list = new ArrayList<>(Arrays.asList(values));
        if (selected != null && !selected.isEmpty() && !list.contains(selected)) list.add(0, selected);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, list) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convertView, parent);
                t.setTextColor(text); t.setTextSize(13); t.setPadding(dp(13),0,dp(13),0); return t;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getDropDownView(position, convertView, parent);
                t.setTextColor(Color.rgb(25,30,30)); t.setTextSize(14); t.setPadding(dp(16),dp(12),dp(16),dp(12)); return t;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if (selected != null && !selected.isEmpty()) {
            int pos = list.indexOf(selected);
            if (pos >= 0) spinner.setSelection(pos);
        }
    }


    private void updateVariantChoices(Spinner variant, String brand, String model, int year, String preferred) {
        String[] values = TurkeyVehicleSpecs.variantLabels(brand, model, year);
        String selected = preferred == null ? "" : preferred;
        if (!selected.isEmpty()) {
            boolean exists = false;
            for (String value : values) if (selected.equals(value)) { exists = true; break; }
            if (!exists) selected = "";
        }
        setSpinnerItems(variant, values, selected);
    }

    private void applySelectedVehicleSpec(Spinner type, Spinner brand, Spinner model, Spinner year, Spinner variant,
                                          Spinner body, Spinner fuel, Spinner transmission,
                                          EditText generation, EditText trim, EditText engine, EditText power,
                                          EditText drivetrain, TextView state) {
        String selectedBrand = String.valueOf(brand.getSelectedItem());
        String selectedModel = String.valueOf(model.getSelectedItem());
        int selectedYear = safeInt(String.valueOf(year.getSelectedItem()), Calendar.getInstance().get(Calendar.YEAR));
        String selectedVariant = String.valueOf(variant.getSelectedItem());
        TurkeyVehicleSpecs.Spec spec = TurkeyVehicleSpecs.find(selectedBrand, selectedModel, selectedYear, selectedVariant);
        boolean exact = spec != null;
        if (spec == null) spec = TurkeyVehicleSpecs.inferred(String.valueOf(type.getSelectedItem()), selectedBrand, selectedModel, selectedYear);

        if (spec != null) {
            if (!spec.body.isEmpty()) selectSpinnerValue(body, spec.body);
            if (!spec.fuel.isEmpty()) selectSpinnerValue(fuel, spec.fuel);
            if (!spec.transmission.isEmpty()) selectSpinnerValue(transmission, spec.transmission);
            if (exact || generation.getText().toString().trim().isEmpty()) generation.setText(spec.generation);
            if (exact || trim.getText().toString().trim().isEmpty()) trim.setText(spec.trim);
            if (exact || engine.getText().toString().trim().isEmpty()) engine.setText(spec.engine);
            if (exact || power.getText().toString().trim().isEmpty()) power.setText(spec.power);
            if (exact || drivetrain.getText().toString().trim().isEmpty()) drivetrain.setText(spec.drivetrain);
        }
        if (exact) {
            state.setText("✓ Teknik veriler katalogdan otomatik dolduruldu. İstersen alanları değiştirebilirsin.");
            state.setTextColor(success);
        } else {
            state.setText("Bu model/yıl için ayrıntılı varyant henüz katalogda yok. Temel alanlar dolduruldu; teknik bilgileri manuel tamamlayabilirsin.");
            state.setTextColor(warning);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void selectSpinnerValue(Spinner spinner, String value) {
        if (value == null || value.trim().isEmpty()) return;
        for (int i = 0; i < spinner.getCount(); i++) {
            if (value.equals(String.valueOf(spinner.getItemAtPosition(i)))) {
                spinner.setSelection(i);
                return;
            }
        }
        if (spinner.getAdapter() instanceof ArrayAdapter) {
            ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
            adapter.add(value);
            adapter.notifyDataSetChanged();
            spinner.setSelection(adapter.getCount() - 1);
        }
    }

    private View cvPhotoThumb(Uri uri) {
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(0,0,dp(8),0);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(cardDrawable(surface2,dp(12),stroke));
        try { image.setImageURI(uri); } catch (Exception ignored) { }
        frame.addView(image,new FrameLayout.LayoutParams(dp(92),dp(78)));

        TextView remove = tv("×",Color.WHITE,17,true);
        remove.setGravity(Gravity.CENTER);
        remove.setBackground(cardDrawable(Color.argb(215,35,42,42),dp(14),Color.TRANSPARENT));
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(28),dp(28));
        rp.gravity = Gravity.TOP | Gravity.RIGHT;
        rp.setMargins(0,dp(4),dp(12),0);
        frame.addView(remove,rp);
        remove.setOnClickListener(v -> {
            cvPhotoUris.remove(uri);
            renderPage(3);
        });
        return frame;
    }

    private TextView formLabel(String value) {
        TextView t = tv(value,muted,10,true);
        t.setPadding(dp(2),0,0,dp(5));
        return t;
    }

    private TextView formSection(String title,String subtitle) {
        TextView t = tv(title + "\n" + subtitle,text,14,true);
        t.setLineSpacing(dp(3),1f);
        t.setPadding(0,0,0,dp(12));
        return t;
    }

    private void detailRow(LinearLayout parent,String label,String value) {
        gap(parent,12);
        TextView l = tv(label.toUpperCase(Locale.getDefault()),muted,9,true);
        parent.addView(l);
        TextView v = tv(value,text,12,false);
        v.setPadding(0,dp(3),0,0);
        parent.addView(v);
    }

    private void addHeader(LinearLayout content,String title,String subtitle) {
        content.addView(tv(title,text,27,true));
        TextView sub = tv(subtitle,muted,11,false);
        sub.setPadding(0,dp(3),0,0);
        content.addView(sub);
        gap(content,17);
    }

    private void addBackHeader(LinearLayout content,String title,String subtitle,Runnable backAction) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = tv("‹",text,34,false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> backAction.run());
        row.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(tv(title,text,22,true));
        titles.addView(tv(subtitle,muted,10,false));
        row.addView(titles,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        content.addView(row);
        gap(content,13);
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(cardDrawable(surface,dp(22),stroke));
        return v;
    }

    private GradientDrawable inputDrawable() { return cardDrawable(surface2,dp(14),stroke); }

    private GradientDrawable cardDrawable(int color,int radius,int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if(strokeColor != Color.TRANSPARENT) d.setStroke(dp(1),strokeColor);
        return d;
    }

    private TextView tv(String value,int color,int sp,boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(sp);
        if(bold) t.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        t.setLineSpacing(0,1.08f);
        return t;
    }

    private TextView pill(String value,int color,int background) {
        TextView t = tv(value,color,10,true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(11),dp(7),dp(11),dp(7));
        t.setBackground(cardDrawable(background,dp(14),Color.TRANSPARENT));
        return t;
    }

    private TextView infoChip(String value) {
        TextView t = tv(value,muted,11,true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(cardDrawable(surface2,dp(14),stroke));
        return t;
    }

    private Button primaryButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        b.setBackground(cardDrawable(accent,dp(16),Color.TRANSPARENT));
        return b;
    }

    private Button compactPrimary(String value) { return primaryButton(value); }

    private Button secondaryButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextColor(text);
        b.setTextSize(11);
        b.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        b.setBackground(cardDrawable(surface2,dp(14),stroke));
        return b;
    }

    private Button dangerButton(String value) {
        Button b = secondaryButton(value);
        b.setTextColor(danger);
        return b;
    }

    private LinearLayout infoCard(String title,String subtitle,int color) {
        LinearLayout b = card();
        b.setPadding(dp(15),dp(14),dp(15),dp(14));
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        TextView dot = tv("●",color,11,true);
        line.addView(dot,new LinearLayout.LayoutParams(dp(23),ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(tv(title,text,13,true));
        texts.addView(tv(subtitle,muted,10,false));
        line.addView(texts,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        b.addView(line);
        return b;
    }

    private LinearLayout statCard(String label,String value,String caption) {
        LinearLayout c = card();
        c.setPadding(dp(14),dp(13),dp(14),dp(13));
        c.addView(tv(label,muted,9,true));
        c.addView(tv(value,text,19,true));
        c.addView(tv(caption,muted,9,false));
        return c;
    }

    private View upcomingCard(AppDatabase.Record r) {
        LinearLayout c = card();
        c.setPadding(dp(15),dp(13),dp(15),dp(13));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView dot = tv("●",warning,11,true);
        row.addView(dot,new LinearLayout.LayoutParams(dp(24),ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(tv(r.title,text,14,true));
        String next = !r.nextDate.isEmpty() ? r.nextDate : (r.nextKm>0 ? formatInt(r.nextKm)+" km" : "Planlı işlem");
        texts.addView(tv(r.type+"  •  "+next,muted,10,false));
        row.addView(texts,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        c.addView(row);
        c.setOnClickListener(v -> openRecordDetail(r.id));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,dp(9));
        c.setLayoutParams(lp);
        return c;
    }

    private void sectionTitle(LinearLayout p,String title,String sub) {
        p.addView(tv(title,text,17,true));
        TextView s = tv(sub,muted,10,false);
        s.setPadding(0,dp(2),0,dp(9));
        p.addView(s);
    }

    private void gap(LinearLayout p,int h) { p.addView(new Space(this),new LinearLayout.LayoutParams(1,dp(h))); }
    private void gapHorizontal(LinearLayout p,int w) { p.addView(new Space(this),new LinearLayout.LayoutParams(dp(w),1)); }
    private int dp(int v) { return(int)(v*getResources().getDisplayMetrics().density+0.5f); }

    private String moduleTitle(String m) {
        if("Bakım".equals(m)) return "Bakım & Onarım";
        if("Sigorta/Kasko".equals(m)) return "Sigorta & Kasko";
        return m;
    }

    private String moduleSubtitle(String m) {
        if("Bakım".equals(m)) return "Bakım, değişen parçalar ve sonraki işlem";
        if("Hasar".equals(m)) return "Hasar, onarım ve belgeler";
        if("Ekspertiz".equals(m)) return "Ekspertiz rapor geçmişi";
        if("Muayene".equals(m)) return "Muayene sonucu ve sonraki tarih";
        if("Vergi".equals(m)) return "Vergi ve ödeme geçmişi";
        if("Sigorta/Kasko".equals(m)) return "Poliçe ve bitiş tarihleri";
        if("Yakıt".equals(m)) return "Yakıt, litre/kWh, tutar ve km";
        return "Diğer araç harcamaları";
    }

    private String moduleMark(String m) {
        if("Bakım".equals(m)) return "B";
        if("Hasar".equals(m)) return "H";
        if("Ekspertiz".equals(m)) return "E";
        if("Muayene".equals(m)) return "M";
        if("Vergi".equals(m)) return "V";
        if("Sigorta/Kasko".equals(m)) return "S";
        if("Yakıt".equals(m)) return "Y";
        return "₺";
    }

    private int moduleColor(String m) {
        if("Hasar".equals(m)) return danger;
        if("Ekspertiz".equals(m)) return warning;
        if("Muayene".equals(m)) return Color.rgb(87,147,255);
        if("Vergi".equals(m)) return Color.rgb(167,111,235);
        if("Sigorta/Kasko".equals(m)) return Color.rgb(62,157,191);
        if("Yakıt".equals(m)) return Color.rgb(237,146,41);
        return accent;
    }

    private String normalizeModule(String type) {
        if("Sigorta".equals(type)||"Kasko".equals(type)) return "Sigorta/Kasko";
        return type;
    }

    private String moduleEmptyText(String module) {
        if("Yakıt".equals(module)) return "İlk dolumunu litre/kWh, tutar ve kilometre bilgisiyle kaydet.";
        if("Bakım".equals(module)) return "İlk bakım kaydını ekleyerek aracın servis geçmişini oluşturmaya başla.";
        return "İlk "+moduleTitle(module).toLowerCase(new Locale("tr","TR"))+" kaydını ekleyebilirsin.";
    }

    private String titleHint(String m) {
        if("Bakım".equals(m)) return "İşlem başlığı";
        if("Hasar".equals(m)) return "Hasar başlığı";
        if("Ekspertiz".equals(m)) return "Firma / rapor başlığı";
        if("Muayene".equals(m)) return "Muayene başlığı";
        if("Vergi".equals(m)) return "Dönem / kayıt başlığı";
        if("Sigorta/Kasko".equals(m)) return "Sigorta şirketi / başlık";
        if("Yakıt".equals(m)) return "İstasyon / dolum başlığı";
        return "Başlık";
    }

    private String defaultTitle(String m) {
        if("Bakım".equals(m)) return "Periyodik bakım";
        if("Hasar".equals(m)) return "Hasar kaydı";
        if("Ekspertiz".equals(m)) return "Ekspertiz";
        if("Muayene".equals(m)) return "Periyodik muayene";
        if("Vergi".equals(m)) return "MTV ödemesi";
        if("Sigorta/Kasko".equals(m)) return "Poliçe";
        if("Yakıt".equals(m)) return "Yakıt alımı";
        return m;
    }

    private String fuelRecordDefault(String vehicleFuel) {
        if(vehicleFuel==null) return "Benzin";
        if(vehicleFuel.contains("LPG")) return "LPG";
        if(vehicleFuel.contains("Dizel")) return "Dizel";
        if(vehicleFuel.contains("Elektrik")&&!vehicleFuel.contains("Hibrit")) return "Elektrik";
        return "Benzin";
    }

    private void pickVehicleImage() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i,PICK_VEHICLE_IMAGE);
    }

    private void pickGalleryImage() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i,PICK_GALLERY_IMAGE);
    }


    private void pickCvImages() {
        if (cvPhotoUris.size() >= VehicleCvPdf.MAX_EXTRA_PHOTOS) {
            toast("En fazla 10 araç resmi ekleyebilirsin");
            return;
        }
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(i, "CV için fotoğraf seç"), PICK_CV_IMAGES);
    }

    private void pickDamageImages(long recordId) {
        int current = db.countRecordPhotos(recordId);
        if (current >= 10) { toast("Bir hasar kaydına en fazla 10 fotoğraf ekleyebilirsin"); return; }
        pendingDamagePhotoRecordId = recordId;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(i, "Hasar fotoğraflarını seç"), PICK_DAMAGE_IMAGES);
    }

    private void pickRecordAttachment(long id) {
        pendingAttachmentRecordId=id;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","application/pdf"});
        startActivityForResult(i,PICK_RECORD_ATTACHMENT);
    }

    @Override
protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (resultCode != RESULT_OK) {
            if (requestCode == CAPTURE_OCR_IMAGE) cleanupOcrCameraImage();
            return;
        }

        if (requestCode == CAPTURE_OCR_IMAGE) {
            Uri cameraUri = pendingOcrCameraUri;
            if (cameraUri != null) processOcrImage(cameraUri, true);
            else toast("Kamera fotoğrafı alınamadı");
            return;
        }

        if (requestCode == VoiceInput.REQUEST_CODE) {
            String spoken = VoiceInput.extract(data);
            if (spoken.isEmpty()) {
                if (assistantInputActive && assistantStatus != null) {
                    assistantInputActive = false;
                    assistantStatus.setText("Ses anlaşılamadı — tekrar deneyebilirsin.");
                    assistantStatus.setTextColor(warning);
                } else if (pendingSmartForm != null && pendingSmartForm.smartStatus != null) {
                    pendingSmartForm.smartStatus.setText("Ses anlaşılamadı — alanları manuel girebilirsin.");
                    pendingSmartForm.smartStatus.setTextColor(warning);
                }
                toast("Ses anlaşılamadı");
            } else {
                RecordParser.Parsed parsed = RecordParser.fromText(spoken);
                if (assistantInputActive) handleAssistantResult(parsed, "Ses");
                else handleSmartResult(parsed, "Ses");
            }
            return;
        }

        if (requestCode == PICK_OCR_IMAGE) {
            if (data != null && data.getData() != null) processOcrImage(data.getData(), false);
            else toast("Fotoğraf seçilemedi");
            return;
        }

        if (data == null) return;

        if (requestCode == PICK_CV_IMAGES) {
            int added = 0;
            android.content.ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount() && cvPhotoUris.size() < VehicleCvPdf.MAX_EXTRA_PHOTOS; i++) {
                    Uri u = clip.getItemAt(i).getUri();
                    if (addCvPhotoUri(u)) added++;
                }
                if (clip.getItemCount() + cvPhotoUris.size() - added > VehicleCvPdf.MAX_EXTRA_PHOTOS)
                    toast("İlk 10 fotoğraf kullanıldı");
            } else if (data.getData() != null) {
                if (addCvPhotoUri(data.getData())) added++;
            }
            if (added > 0) toast(added + " araç resmi eklendi");
            else if (cvPhotoUris.isEmpty()) toast("Fotoğraf seçilemedi");
            renderPage(3);
            return;
        }

        if (requestCode == PICK_DAMAGE_IMAGES && pendingDamagePhotoRecordId >= 0) {
            long recordId = pendingDamagePhotoRecordId;
            pendingDamagePhotoRecordId = -1;
            int available = Math.max(0, 10 - db.countRecordPhotos(recordId));
            int added = 0;
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            android.content.ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount() && added < available; i++) {
                    Uri u = clip.getItemAt(i).getUri();
                    if (u == null) continue;
                    try { getContentResolver().takePersistableUriPermission(u, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
                    db.addRecordPhoto(recordId, u.toString(), today());
                    added++;
                }
                if (clip.getItemCount() > available) toast("İlk " + available + " fotoğraf eklendi; sınır 10");
            } else if (data.getData() != null && available > 0) {
                Uri u = data.getData();
                try { getContentResolver().takePersistableUriPermission(u, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
                db.addRecordPhoto(recordId, u.toString(), today());
                added = 1;
            }
            if (added > 0) toast(added + " hasar fotoğrafı eklendi");
            openRecordDetail(recordId);
            return;
        }

        if (data.getData() == null) return;
        Uri uri=data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch(Exception ignored) {}
        if(requestCode==PICK_VEHICLE_IMAGE) {
            prefs.edit().putString("vehicle_photo_uri",uri.toString()).apply();
            renderPage(0);
        } else if(requestCode==PICK_GALLERY_IMAGE) {
            db.addPhoto(uri.toString(),today());
            renderPage(0);
        } else if(requestCode==PICK_RECORD_ATTACHMENT&&pendingAttachmentRecordId>=0) {
            db.setRecordAttachment(pendingAttachmentRecordId,uri.toString());
            long id=pendingAttachmentRecordId;
            pendingAttachmentRecordId=-1;
            openRecordDetail(id);
        }
    }

    private boolean addCvPhotoUri(Uri uri) {
        if (uri == null || cvPhotoUris.size() >= VehicleCvPdf.MAX_EXTRA_PHOTOS) return false;
        String value = uri.toString();
        for (Uri existing : cvPhotoUris) if (existing != null && value.equals(existing.toString())) return false;
        cvPhotoUris.add(uri);
        return true;
    }

    private void openAttachment(String u) {
        try {
            Intent i=new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(u));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch(Exception e) {
            toast("Belge açılamadı");
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},401);
        }
    }

    private boolean notificationsEnabled() {
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        return Build.VERSION.SDK_INT<24||nm.areNotificationsEnabled();
    }

private void createVehiclePdf() {
        try {
            VehicleCvPdf.Result result = VehicleCvPdf.create(this, db, prefs, cvPhotoUris);
            toast("Araç CV oluşturuldu • Belge No: " + result.documentNo);
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) openPdf(result.uri);
        } catch(Exception e) {
            toast("PDF oluşturulamadı: " + e.getMessage());
        }
    }

    private void openPdf(Uri uri) {
        try {
            Intent i=new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri,"application/pdf");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch(Exception ignored) {}
    }

    private String today() { return new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).format(new Date()); }

    private String formatMoney(double v) {
        NumberFormat nf=NumberFormat.getNumberInstance(new Locale("tr","TR"));
        nf.setMaximumFractionDigits(2);
        return nf.format(v)+" ₺";
    }

    private String formatInt(int v) { return NumberFormat.getIntegerInstance(new Locale("tr","TR")).format(v); }

    private String trimDouble(double v) {
        if(Math.abs(v-Math.rint(v))<0.00001) return String.valueOf((long)Math.rint(v));
        return String.format(Locale.getDefault(),"%.2f",v).replaceAll("0+$","").replaceAll("[.,]$","");
    }

    private String moneyRaw(double v) { return v>0?trimDouble(v):""; }
    private int safeInt(String s,int f) { try{return Integer.parseInt(s.trim());}catch(Exception e){return f;} }
    private double safeDouble(String s) { try{return Double.parseDouble(s.trim().replace(',','.'));}catch(Exception e){return 0;} }

    private String join(List<String> values,String sep) {
        StringBuilder b=new StringBuilder();
        for(String v:values) {
            if(b.length()>0) b.append(sep);
            b.append(v);
        }
        return b.toString();
    }

    private String truncate(String s,int max) { return s.length()<=max?s:s.substring(0,max-1)+"…"; }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
}
