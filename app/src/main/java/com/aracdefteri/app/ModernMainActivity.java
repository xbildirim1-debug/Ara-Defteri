package com.aracdefteri.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
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
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ModernMainActivity extends Activity {
    private static final int PICK_GALLERY_IMAGE = 2101;
    private static final int PICK_RECORD_ATTACHMENT = 2102;
    private static final int PICK_VEHICLE_IMAGE = 2103;
    private static final String PREFS = "arac_defteri_prefs";

    private MainActivity.Database db;
    private SharedPreferences prefs;
    private LinearLayout pageHost;
    private LinearLayout navBar;
    private int currentPage = 0;
    private String currentModule = null;
    private long pendingAttachmentRecordId = -1;
    private boolean dark;

    private int bg, surface, surface2, text, muted, accent, accent2, warning, danger, stroke;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        db = new MainActivity.Database(this);
        db.seedDemoIfNeeded();
        resolveTheme();
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
            bg = Color.rgb(8, 10, 17);
            surface = Color.rgb(18, 22, 31);
            surface2 = Color.rgb(29, 35, 48);
            text = Color.rgb(247, 249, 252);
            muted = Color.rgb(154, 163, 180);
            accent = Color.rgb(88, 166, 255);
            accent2 = Color.rgb(126, 92, 255);
            warning = Color.rgb(255, 184, 76);
            danger = Color.rgb(255, 99, 115);
            stroke = Color.rgb(44, 51, 68);
        } else {
            bg = Color.rgb(246, 247, 251);
            surface = Color.WHITE;
            surface2 = Color.rgb(238, 242, 249);
            text = Color.rgb(23, 26, 36);
            muted = Color.rgb(103, 111, 128);
            accent = Color.rgb(47, 111, 237);
            accent2 = Color.rgb(111, 77, 255);
            warning = Color.rgb(205, 128, 14);
            danger = Color.rgb(214, 67, 83);
            stroke = Color.rgb(221, 226, 236);
        }
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
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
        navBar.setPadding(dp(8), dp(7), dp(8), dp(9));
        navBar.setBackground(cardDrawable(surface, 0, stroke));
        root.addView(navBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));

        setContentView(root);
        buildBottomNav();
    }

    private void buildBottomNav() {
        navBar.removeAllViews();
        String[] labels = {"Ana Sayfa", "Kayıtlar", "Galeri", "Araç CV", "Ayarlar"};
        String[] icons = {"⌂", "≡", "▦", "CV", "⚙"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(3), dp(4), dp(3), dp(3));
            if (currentModule == null && currentPage == i) item.setBackground(cardDrawable(surface2, dp(16), Color.TRANSPARENT));
            TextView icon = tv(icons[i], currentModule == null && currentPage == i ? accent : muted, 16, true);
            icon.setGravity(Gravity.CENTER);
            TextView label = tv(labels[i], currentModule == null && currentPage == i ? text : muted, 10, currentModule == null && currentPage == i);
            label.setGravity(Gravity.CENTER);
            item.addView(icon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(23)));
            item.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));
            item.setOnClickListener(v -> renderPage(index));
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
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(content);
        pageHost.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return content;
    }

    private void renderPage(int page) {
        currentModule = null;
        currentPage = page;
        buildBottomNav();
        LinearLayout content = newContent();
        if (page == 0) renderHome(content);
        else if (page == 1) renderRecordsHub(content);
        else if (page == 2) renderGallery(content);
        else if (page == 3) renderVehicleCv(content);
        else renderSettings(content);
    }

    private void openModule(String module) {
        currentModule = module;
        currentPage = 1;
        buildBottomNav();
        LinearLayout content = newContent();
        if ("Giderler".equals(module)) renderExpensesModule(content);
        else renderRecordModule(content, module);
    }

    @Override
    public void onBackPressed() {
        if (currentModule != null) renderPage(1);
        else super.onBackPressed();
    }

    private void addHeader(LinearLayout content, String title, String subtitle) {
        content.addView(tv(title, text, 28, true));
        TextView sub = tv(subtitle, muted, 12, false);
        sub.setPadding(0, dp(3), 0, 0);
        content.addView(sub);
        gap(content, 18);
    }

    private void addSubHeader(LinearLayout content, String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = tv("‹", text, 34, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> renderPage(1));
        row.addView(back, new LinearLayout.LayoutParams(dp(45), dp(48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(tv(title, text, 23, true));
        titles.addView(tv(subtitle, muted, 11, false));
        row.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(row);
        gap(content, 14);
    }

    private void renderHome(LinearLayout content) {
        addHeader(content, "Araç Defteri", "Aracının dijital hafızası");
        MainActivity.Vehicle vehicle = db.getVehicle();
        content.addView(vehicleHero(vehicle));
        gap(content, 20);

        sectionTitle(content, "Yaklaşanlar", "Bakım ve resmî tarihleri tek bakışta gör");
        List<MainActivity.Record> upcoming = db.getUpcomingRecords(3);
        if (upcoming.isEmpty()) content.addView(infoCard("Yaklaşan kayıt yok", "Bakım veya resmî bir kayıt eklediğinde hatırlatmalar burada görünür.", accent));
        else for (MainActivity.Record r : upcoming) content.addView(upcomingCard(r));

        gap(content, 20);
        sectionTitle(content, "Özet", "Gereksiz kalabalık olmadan önemli bilgiler");
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(statCard("BU AY", formatMoney(db.getCurrentMonthExpenseTotal())), new LinearLayout.LayoutParams(0, dp(92), 1f));
        Space sp = new Space(this);
        stats.addView(sp, new LinearLayout.LayoutParams(dp(10), 1));
        stats.addView(statCard("TOPLAM KAYIT", String.valueOf(db.countAllRecords())), new LinearLayout.LayoutParams(0, dp(92), 1f));
        content.addView(stats);

        gap(content, 20);
        sectionTitle(content, "Son hareketler", "Bir kayda dokununca kendi bölümü açılır");
        List<MainActivity.Record> recent = db.getRecords(4);
        for (MainActivity.Record r : recent) {
            LinearLayout card = recordCard(r, false);
            card.setOnClickListener(v -> openModule(normalizeModule(r.type)));
            content.addView(card);
        }
    }

    private LinearLayout vehicleHero(MainActivity.Vehicle vehicle) {
        LinearLayout outer = card();
        outer.setPadding(dp(7), dp(7), dp(7), dp(16));

        FrameLayout media = new FrameLayout(this);
        GradientDrawable mediaBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                dark ? new int[]{Color.rgb(30, 61, 117), Color.rgb(80, 50, 145)} : new int[]{Color.rgb(55, 121, 240), Color.rgb(112, 75, 255)});
        mediaBg.setCornerRadius(dp(22));
        media.setBackground(mediaBg);
        media.setClipToOutline(true);

        String photoUri = prefs.getString("vehicle_photo_uri", "");
        if (!photoUri.isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try { image.setImageURI(Uri.parse(photoUri)); } catch (Exception ignored) {}
            media.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            TextView placeholder = tv("ARACININ FOTOĞRAFINI EKLE", Color.WHITE, 16, true);
            placeholder.setGravity(Gravity.CENTER);
            media.addView(placeholder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        TextView badge = tv("DEMO", Color.WHITE, 10, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(cardDrawable(Color.argb(150, 10, 12, 20), dp(13), Color.TRANSPARENT));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(62), dp(30));
        bp.gravity = Gravity.TOP | Gravity.LEFT;
        bp.setMargins(dp(12), dp(12), 0, 0);
        media.addView(badge, bp);

        media.setOnClickListener(v -> pickVehicleImage());
        outer.addView(media, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)));
        gap(outer, 14);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(tv(vehicle.year + "  " + vehicle.brand + " " + vehicle.model, text, 20, true));
        names.addView(tv(vehicle.plate + "   •   " + formatInt(vehicle.km) + " km", muted, 12, false));
        info.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView edit = tv("Düzenle", accent, 12, true);
        edit.setGravity(Gravity.CENTER);
        edit.setPadding(dp(12), dp(9), dp(12), dp(9));
        edit.setBackground(cardDrawable(surface2, dp(14), Color.TRANSPARENT));
        edit.setOnClickListener(v -> showVehicleDialog());
        info.addView(edit);
        outer.addView(info);

        TextView photoHint = tv(photoUri.isEmpty() ? "Fotoğraf eklemek için üst alana dokun" : "Fotoğrafı değiştirmek için üst alana dokun", muted, 10, false);
        photoHint.setPadding(dp(10), dp(8), dp(10), 0);
        outer.addView(photoHint);
        return outer;
    }

    private void renderRecordsHub(LinearLayout content) {
        addHeader(content, "Kayıtlar", "Her işlem kendi sayfasında");
        String[] modules = {"Bakım", "Hasar", "Ekspertiz", "Muayene", "Vergi", "Sigorta/Kasko", "Yakıt", "Giderler"};
        for (String module : modules) content.addView(moduleRow(module));
    }

    private LinearLayout moduleRow(String module) {
        LinearLayout row = card();
        row.setPadding(dp(16), dp(15), dp(14), dp(15));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView icon = tv(moduleIcon(module), Color.WHITE, 16, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(cardDrawable(moduleColor(module), dp(16), Color.TRANSPARENT));
        row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(13), 0, 0, 0);
        texts.addView(tv(moduleTitle(module), text, 16, true));
        texts.addView(tv(moduleSubtitle(module), muted, 11, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String count = "Giderler".equals(module) ? formatMoney(db.getExpenseTotal()) : String.valueOf(db.countRecordsByType(module));
        TextView right = tv(count + "  ›", muted, 12, true);
        row.addView(right);
        row.setOnClickListener(v -> openModule(module));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(lp);
        return row;
    }

    private void renderRecordModule(LinearLayout content, String module) {
        addSubHeader(content, moduleTitle(module), moduleSubtitle(module));
        Button add = primaryButton("+ " + moduleTitle(module) + " kaydı ekle");
        add.setOnClickListener(v -> showRecordDialog(module));
        content.addView(add);
        gap(content, 14);
        content.addView(infoCard(moduleInfoTitle(module), moduleInfo(module), moduleColor(module)));
        gap(content, 16);

        List<MainActivity.Record> records = recordsFor(module);
        if (records.isEmpty()) content.addView(infoCard("Henüz kayıt yok", "İlk kaydını ekleyerek bu bölümün geçmişini oluşturmaya başlayabilirsin.", accent));
        else for (MainActivity.Record r : records) content.addView(recordCard(r, true));
    }

    private void renderExpensesModule(LinearLayout content) {
        addSubHeader(content, "Giderler", "Bakım dışındaki araç harcamaları");
        LinearLayout total = card();
        total.setPadding(dp(18), dp(18), dp(18), dp(18));
        total.addView(tv("TOPLAM KAYITLI GİDER", muted, 11, true));
        total.addView(tv(formatMoney(db.getExpenseTotal()), text, 30, true));
        content.addView(total);
        gap(content, 12);
        Button add = primaryButton("+ Gider ekle");
        add.setOnClickListener(v -> showExpenseDialog());
        content.addView(add);
        gap(content, 18);
        for (MainActivity.Expense e : db.getExpenses()) {
            LinearLayout c = card();
            c.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.addView(tv(e.category, text, 15, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            line.addView(tv(formatMoney(e.amount), accent, 15, true));
            c.addView(line);
            c.addView(tv(e.date + (e.note.isEmpty() ? "" : "  •  " + e.note), muted, 11, false));
            content.addView(c);
            gap(content, 9);
        }
    }

    private void renderGallery(LinearLayout content) {
        addHeader(content, "Galeri", "Aracının fotoğraf hikâyesi");
        Button add = primaryButton("+ Fotoğraf ekle");
        add.setOnClickListener(v -> pickGalleryImage());
        content.addView(add);
        gap(content, 14);
        content.addView(infoCard("Galeri ayrı, hasar belgeleri ayrı", "Normal araç fotoğrafların burada; hasar ve ekspertiz görselleri kendi kayıtlarında saklanır.", accent));
        gap(content, 16);

        List<MainActivity.Photo> photos = db.getPhotos();
        if (photos.isEmpty()) {
            LinearLayout empty = card();
            empty.setPadding(dp(18), dp(34), dp(18), dp(34));
            TextView e = tv("Henüz fotoğraf yok", text, 17, true);
            e.setGravity(Gravity.CENTER);
            empty.addView(e);
            TextView s = tv("Yıkama sonrası, gezi veya aracını ilk aldığın gün gibi fotoğrafları ekleyebilirsin.", muted, 11, false);
            s.setGravity(Gravity.CENTER);
            s.setPadding(0, dp(6), 0, 0);
            empty.addView(s);
            content.addView(empty);
        } else {
            for (MainActivity.Photo p : photos) {
                LinearLayout c = card();
                c.setPadding(dp(7), dp(7), dp(7), dp(12));
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                try { image.setImageURI(Uri.parse(p.uri)); } catch (Exception ignored) {}
                c.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
                TextView cap = tv(p.date + "  •  Araç galerisi", muted, 11, false);
                cap.setPadding(dp(9), dp(9), dp(9), 0);
                c.addView(cap);
                content.addView(c);
                gap(content, 10);
            }
        }
    }

    private void renderVehicleCv(LinearLayout content) {
        addHeader(content, "Araç CV", "Satışta veya arşivde kullanılacak kapsamlı geçmiş");
        MainActivity.Vehicle v = db.getVehicle();
        LinearLayout preview = card();
        preview.setPadding(dp(20), dp(20), dp(20), dp(20));
        preview.addView(tv("DİJİTAL ARAÇ GEÇMİŞİ", accent2, 11, true));
        preview.addView(tv(v.year + "  " + v.brand + " " + v.model, text, 23, true));
        preview.addView(tv(formatInt(v.km) + " km", muted, 12, false));
        gap(preview, 15);
        preview.addView(cvLine("Bakım / onarım", db.countRecordsByType("Bakım") + " kayıt"));
        preview.addView(cvLine("Hasar", db.countRecordsByType("Hasar") + " kayıt"));
        preview.addView(cvLine("Ekspertiz", db.countRecordsByType("Ekspertiz") + " kayıt"));
        preview.addView(cvLine("Muayene", db.countRecordsByType("Muayene") + " kayıt"));
        preview.addView(cvLine("Toplam kayıt", db.countAllRecords() + " kayıt"));
        content.addView(preview);
        gap(content, 16);

        sectionTitle(content, "CV içeriği", "PDF artık sadece basit kayıt listesi değil");
        content.addView(featureLine("Kapak", "Araç fotoğrafı, model, kilometre ve isteğe bağlı plaka"));
        content.addView(featureLine("Zaman çizelgesi", "Bakım, hasar, ekspertiz ve resmî işlemler kronolojik"));
        content.addView(featureLine("Bakım geçmişi", "Yapılan işlem, değişen parça, tarih, km ve notlar"));
        content.addView(featureLine("Hasar ve ekspertiz", "Ayrı bölümler halinde detaylı geçmiş"));
        content.addView(featureLine("Resmî kayıtlar", "Muayene, vergi ve sigorta/kasko geçmişi"));
        content.addView(featureLine("Maliyet özeti", "İstersen fiyatları PDF'de göster veya gizle"));
        content.addView(featureLine("İletişim", "Sadece isteğe bağlı telefon numarası ve özel açıklama"));
        content.addView(featureLine("Gizlilik", "Şase/VIN, T.C. kimlik no ve adres alanı yok"));
        gap(content, 14);

        Button create = primaryButton("CV seçenekleri ve PDF oluştur");
        create.setOnClickListener(vw -> showCvDialog());
        content.addView(create);
    }

    private void renderSettings(LinearLayout content) {
        addHeader(content, "Ayarlar", "Uygulama görünümünü ve gizliliği yönet");
        sectionTitle(content, "Tema", "Tercihin uygulama genelinde kalıcı olur");
        String mode = prefs.getString("theme_mode", "system");
        content.addView(themeOption("Sistem", "Telefonun açık/koyu temasını takip et", "system", mode));
        content.addView(themeOption("Açık", "Ferahlık ve yüksek okunabilirlik", "light", mode));
        content.addView(themeOption("Koyu", "Gece kullanımı ve kokpit hissi", "dark", mode));
        gap(content, 20);
        sectionTitle(content, "Gizlilik", "Araç CV için belirlediğimiz sınırlar");
        content.addView(infoCard("Şase/VIN yok", "Uygulama şase numarası istemez, saklamaz veya Araç CV'ye yazmaz.", accent));
        gap(content, 10);
        content.addView(infoCard("CV kişisel bilgileri sınırlı", "CV'de yalnızca isteğe bağlı telefon numarası ve özel açıklama alanı bulunur.", accent2));
    }

    private LinearLayout themeOption(String titleValue, String subtitle, String value, String selected) {
        LinearLayout row = card();
        row.setPadding(dp(16), dp(15), dp(16), dp(15));
        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.addView(tv(titleValue, text, 15, true));
        textBox.addView(tv(subtitle, muted, 11, false));
        row.addView(textBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tv(value.equals(selected) ? "✓" : "○", value.equals(selected) ? accent : muted, 20, true));
        row.setOnClickListener(v -> {
            prefs.edit().putString("theme_mode", value).apply();
            recreate();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(9));
        row.setLayoutParams(lp);
        return row;
    }

    private void showVehicleDialog() {
        MainActivity.Vehicle v = db.getVehicle();
        LinearLayout form = formLayout();
        EditText brand = field("Marka", v.brand);
        EditText model = field("Model", v.model);
        EditText year = numberField("Model yılı", String.valueOf(v.year));
        EditText plate = field("Plaka", v.plate);
        EditText km = numberField("Kilometre", String.valueOf(v.km));
        form.addView(brand); form.addView(model); form.addView(year); form.addView(plate); form.addView(km);
        new AlertDialog.Builder(this)
                .setTitle("Araç bilgileri")
                .setView(form)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Kaydet", (d, w) -> {
                    db.updateVehicle(brand.getText().toString().trim(), model.getText().toString().trim(), safeInt(year.getText().toString(), v.year), plate.getText().toString().trim(), safeInt(km.getText().toString(), v.km));
                    renderPage(0);
                }).show();
    }

    private void showRecordDialog(String module) {
        LinearLayout form = formLayout();
        MainActivity.Vehicle vehicle = db.getVehicle();
        EditText titleField = field(recordTitleHint(module), defaultTitle(module));
        EditText dateField = field("Tarih (gg.aa.yyyy)", today());
        EditText kmField = numberField("Kilometre", String.valueOf(vehicle.km));
        EditText costField = decimalField("Tutar (isteğe bağlı)", "");
        form.addView(titleField); form.addView(dateField); form.addView(kmField); form.addView(costField);

        Spinner part = null;
        if ("Bakım".equals(module)) {
            form.addView(label("Değişen parça / işlem seçeneği"));
            String[] parts = {"Seçilmedi", "Motor yağı", "Yağ filtresi", "Hava filtresi", "Polen filtresi", "Fren balatası", "Akü", "Lastik", "Triger", "Şanzıman yağı", "Diğer / kendim yazacağım"};
            part = new Spinner(this);
            part.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, parts));
            form.addView(part, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        }

        EditText detailField = field(recordDetailHint(module), "");
        detailField.setMinLines(3);
        detailField.setGravity(Gravity.TOP);
        form.addView(detailField);

        EditText nextDate = null;
        EditText nextKm = null;
        if ("Bakım".equals(module) || "Muayene".equals(module) || "Vergi".equals(module) || "Sigorta/Kasko".equals(module)) {
            nextDate = field("Sonraki tarih / bitiş tarihi (isteğe bağlı)", "");
            form.addView(nextDate);
        }
        if ("Bakım".equals(module)) {
            nextKm = numberField("Sonraki bakım kilometresi (isteğe bağlı)", "");
            form.addView(nextKm);
        }

        final Spinner selectedPart = part;
        final EditText selectedNextDate = nextDate;
        final EditText selectedNextKm = nextKm;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(moduleTitle(module) + " kaydı")
                .setView(form)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Kaydet", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = titleField.getText().toString().trim();
            if (title.isEmpty()) title = moduleTitle(module);
            String detail = detailField.getText().toString().trim();
            if (selectedPart != null && selectedPart.getSelectedItemPosition() > 0) {
                String partText = selectedPart.getSelectedItem().toString();
                detail = "Değişen / yapılan: " + partText + (detail.isEmpty() ? "" : "\n" + detail);
            }
            String nDate = selectedNextDate == null ? "" : selectedNextDate.getText().toString().trim();
            int nKm = selectedNextKm == null ? 0 : safeInt(selectedNextKm.getText().toString(), 0);
            long id = db.addRecord(module, title, dateField.getText().toString().trim(), safeInt(kmField.getText().toString(), 0), safeDouble(costField.getText().toString()), detail, nDate, nKm);
            if (!nDate.isEmpty()) scheduleDateReminders(id, title, nDate);
            dialog.dismiss();
            openModule(module);
        }));
        dialog.show();
    }

    private void showExpenseDialog() {
        LinearLayout form = formLayout();
        String[] cats = {"Otopark", "Otoyol / geçiş", "Yıkama", "Aksesuar", "Ceza", "Diğer"};
        Spinner category = new Spinner(this);
        category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cats));
        form.addView(label("Kategori"));
        form.addView(category, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        EditText amount = decimalField("Tutar", "");
        EditText date = field("Tarih", today());
        EditText note = field("Açıklama", "");
        form.addView(amount); form.addView(date); form.addView(note);
        new AlertDialog.Builder(this)
                .setTitle("Gider ekle")
                .setView(form)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Kaydet", (d, w) -> {
                    db.addExpense(category.getSelectedItem().toString(), date.getText().toString().trim(), safeDouble(amount.getText().toString()), note.getText().toString().trim());
                    openModule("Giderler");
                }).show();
    }

    private void showCvDialog() {
        LinearLayout form = formLayout();
        CheckBox showPlate = checkbox("Plakayı göster", false);
        CheckBox showCosts = checkbox("Maliyetleri göster", true);
        CheckBox damage = checkbox("Hasar geçmişini ekle", true);
        CheckBox expertise = checkbox("Ekspertiz geçmişini ekle", true);
        CheckBox official = checkbox("Muayene, vergi ve sigorta/kasko geçmişini ekle", true);
        CheckBox gallery = checkbox("Araç fotoğraflarını ekle", true);
        form.addView(showPlate); form.addView(showCosts); form.addView(damage); form.addView(expertise); form.addView(official); form.addView(gallery);
        form.addView(label("İsteğe bağlı kişisel bilgi"));
        EditText phone = field("Telefon numarası (isteğe bağlı)", prefs.getString("cv_phone", ""));
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText note = field("Özel açıklama (isteğe bağlı)", prefs.getString("cv_note", ""));
        note.setMinLines(3);
        note.setGravity(Gravity.TOP);
        form.addView(phone); form.addView(note);
        form.addView(infoCard("Gizlilik", "Şase/VIN, T.C. kimlik no, adres ve benzeri alanlar CV'de bulunmaz.", accent));

        new AlertDialog.Builder(this)
                .setTitle("Araç CV seçenekleri")
                .setView(form)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("PDF oluştur", (d, w) -> {
                    prefs.edit().putString("cv_phone", phone.getText().toString().trim()).putString("cv_note", note.getText().toString().trim()).apply();
                    CvOptions o = new CvOptions();
                    o.showPlate = showPlate.isChecked();
                    o.showCosts = showCosts.isChecked();
                    o.includeDamage = damage.isChecked();
                    o.includeExpertise = expertise.isChecked();
                    o.includeOfficial = official.isChecked();
                    o.includeGallery = gallery.isChecked();
                    o.phone = phone.getText().toString().trim();
                    o.note = note.getText().toString().trim();
                    createVehiclePdf(o);
                }).show();
    }

    private void pickVehicleImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, PICK_VEHICLE_IMAGE);
    }

    private void pickGalleryImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, PICK_GALLERY_IMAGE);
    }

    private void pickRecordAttachment(long id) {
        pendingAttachmentRecordId = id;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});
        startActivityForResult(i, PICK_RECORD_ATTACHMENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
        if (requestCode == PICK_VEHICLE_IMAGE) {
            prefs.edit().putString("vehicle_photo_uri", uri.toString()).apply();
            renderPage(0);
        } else if (requestCode == PICK_GALLERY_IMAGE) {
            db.addPhoto(uri.toString(), today());
            renderPage(2);
        } else if (requestCode == PICK_RECORD_ATTACHMENT && pendingAttachmentRecordId > 0) {
            db.setRecordAttachment(pendingAttachmentRecordId, uri.toString());
            pendingAttachmentRecordId = -1;
            Toast.makeText(this, "Belge / fotoğraf kayda eklendi", Toast.LENGTH_SHORT).show();
            if (currentModule != null) openModule(currentModule); else renderPage(1);
        }
    }

    private void openAttachment(String u) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(u));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Belge açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    private void scheduleDateReminders(long id, String title, String dateText) {
        try {
            Date due = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(dateText);
            if (due == null) return;
            scheduleOneReminder(id, title, due.getTime() - 7L * 86400000L, 7);
            scheduleOneReminder(id, title, due.getTime() - 86400000L, 1);
        } catch (Exception ignored) {}
    }

    private void scheduleOneReminder(long id, String title, long triggerAt, int days) {
        if (triggerAt <= System.currentTimeMillis()) return;
        Intent i = new Intent(this, ReminderReceiver.class);
        i.putExtra("title", title + " yaklaşıyor");
        i.putExtra("text", days + " gün kaldı. Araç Defteri kaydını kontrol et.");
        int request = (int) ((id * 10 + days) % Integer.MAX_VALUE);
        PendingIntent pi = PendingIntent.getBroadcast(this, request, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ((AlarmManager) getSystemService(ALARM_SERVICE)).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 301);
    }

    private void createVehiclePdf(CvOptions o) {
        android.graphics.pdf.PdfDocument doc = new android.graphics.pdf.PdfDocument();
        PdfWriter w = new PdfWriter(doc);
        MainActivity.Vehicle vehicle = db.getVehicle();
        try {
            w.newPage();
            w.title("ARAÇ DEFTERİ • ARAÇ CV");
            Bitmap hero = loadBitmap(prefs.getString("vehicle_photo_uri", ""));
            if (hero != null) w.photo(hero);
            w.big(vehicle.year + "  " + vehicle.brand + " " + vehicle.model);
            w.small(formatInt(vehicle.km) + " km" + (o.showPlate ? "   •   " + vehicle.plate : ""));
            w.gap(10);
            w.section("ARAÇ ÖZETİ");
            w.kv("Bakım / onarım", db.countRecordsByType("Bakım") + " kayıt");
            w.kv("Hasar", db.countRecordsByType("Hasar") + " kayıt");
            w.kv("Ekspertiz", db.countRecordsByType("Ekspertiz") + " kayıt");
            w.kv("Muayene", db.countRecordsByType("Muayene") + " kayıt");
            if (o.showCosts) w.kv("Toplam kayıtlı gider", formatMoney(db.getExpenseTotal()));

            w.section("ZAMAN ÇİZELGESİ");
            for (MainActivity.Record r : db.getRecords(100)) {
                if (!includeRecordInCv(r, o)) continue;
                w.record(r, o.showCosts);
            }

            writeCvSection(w, "BAKIM / ONARIM GEÇMİŞİ", "Bakım", o.showCosts);
            if (o.includeDamage) writeCvSection(w, "HASAR GEÇMİŞİ", "Hasar", o.showCosts);
            if (o.includeExpertise) writeCvSection(w, "EKSPERTİZ GEÇMİŞİ", "Ekspertiz", o.showCosts);
            if (o.includeOfficial) {
                writeCvSection(w, "MUAYENE GEÇMİŞİ", "Muayene", o.showCosts);
                writeCvSection(w, "VERGİ GEÇMİŞİ", "Vergi", o.showCosts);
                writeCvSection(w, "SİGORTA / KASKO GEÇMİŞİ", "Sigorta/Kasko", o.showCosts);
            }

            if (o.includeGallery) {
                List<MainActivity.Photo> photos = db.getPhotos();
                if (!photos.isEmpty()) {
                    w.section("ARAÇ GALERİSİ");
                    int shown = 0;
                    for (MainActivity.Photo p : photos) {
                        Bitmap b = loadBitmap(p.uri);
                        if (b != null) { w.photo(b); w.small(p.date); shown++; }
                        if (shown >= 3) break;
                    }
                }
            }

            if (!o.phone.isEmpty() || !o.note.isEmpty()) {
                w.section("İLETİŞİM / ÖZEL NOT");
                if (!o.phone.isEmpty()) w.kv("Telefon", o.phone);
                if (!o.note.isEmpty()) w.paragraph(o.note);
            }

            w.section("BİLGİLENDİRME");
            w.paragraph("Bu rapor araç sahibi tarafından girilen kayıtlardan oluşturulmuştur. Resmî ekspertiz, servis doğrulaması veya kilometre doğrulama belgesi değildir.");
            w.finishPage();

            String fileName = "Arac-CV-" + System.currentTimeMillis() + ".pdf";
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AracDefteri");
                uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new Exception("Dosya oluşturulamadı");
                try (OutputStream out = getContentResolver().openOutputStream(uri)) { doc.writeTo(out); }
            } else {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                if (dir == null) throw new Exception("Klasör bulunamadı");
                File file = new File(dir, fileName);
                try (OutputStream out = new FileOutputStream(file)) { doc.writeTo(out); }
                uri = Uri.fromFile(file);
            }
            doc.close();
            Toast.makeText(this, "Genişletilmiş Araç CV oluşturuldu", Toast.LENGTH_LONG).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) openPdf(uri);
        } catch (Exception e) {
            try { doc.close(); } catch (Exception ignored) {}
            Toast.makeText(this, "PDF oluşturulamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean includeRecordInCv(MainActivity.Record r, CvOptions o) {
        if ("Hasar".equals(r.type) && !o.includeDamage) return false;
        if ("Ekspertiz".equals(r.type) && !o.includeExpertise) return false;
        if (("Muayene".equals(r.type) || "Vergi".equals(r.type) || "Sigorta/Kasko".equals(r.type)) && !o.includeOfficial) return false;
        return true;
    }

    private void writeCvSection(PdfWriter w, String title, String type, boolean showCosts) {
        List<MainActivity.Record> list = recordsFor(type);
        if (list.isEmpty()) return;
        w.section(title);
        for (MainActivity.Record r : list) w.record(r, showCosts);
    }

    private Bitmap loadBitmap(String uriText) {
        if (uriText == null || uriText.isEmpty()) return null;
        try (InputStream in = getContentResolver().openInputStream(Uri.parse(uriText))) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception ignored) { return null; }
    }

    private void openPdf(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/pdf");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    private List<MainActivity.Record> recordsFor(String type) {
        List<MainActivity.Record> out = new ArrayList<>();
        for (MainActivity.Record r : db.getRecords(200)) if (type.equals(r.type)) out.add(r);
        return out;
    }

    private String normalizeModule(String type) {
        if ("Sigorta".equals(type)) return "Sigorta/Kasko";
        return type;
    }

    private LinearLayout statCard(String labelValue, String value) {
        LinearLayout c = card();
        c.setPadding(dp(15), dp(15), dp(15), dp(15));
        c.addView(tv(labelValue, muted, 10, true));
        TextView v = tv(value, text, 18, true);
        v.setPadding(0, dp(5), 0, 0);
        c.addView(v);
        return c;
    }

    private LinearLayout upcomingCard(MainActivity.Record r) {
        LinearLayout c = card();
        c.setPadding(dp(15), dp(13), dp(15), dp(13));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView dot = tv("●", warning, 13, true);
        row.addView(dot, new LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(tv(r.title, text, 14, true));
        String next = !r.nextDate.isEmpty() ? r.nextDate : (r.nextKm > 0 ? formatInt(r.nextKm) + " km" : "Hatırlatma");
        t.addView(tv(r.type + "  •  " + next, muted, 11, false));
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        c.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(9));
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout recordCard(MainActivity.Record r, boolean actions) {
        LinearLayout c = card();
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView type = tv(r.type.toUpperCase(Locale.getDefault()), moduleColor(normalizeModule(r.type)), 10, true);
        top.addView(type);
        top.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(tv(r.date, muted, 10, false));
        c.addView(top);
        gap(c, 7);
        c.addView(tv(r.title, text, 16, true));
        c.addView(tv(formatInt(r.km) + " km" + (r.cost > 0 ? "  •  " + formatMoney(r.cost) : ""), muted, 11, false));
        if (!r.detail.isEmpty()) {
            TextView d = tv(r.detail, muted, 11, false);
            d.setPadding(0, dp(5), 0, 0);
            c.addView(d);
        }
        if (!r.nextDate.isEmpty() || r.nextKm > 0) {
            String next = "Sonraki: " + (!r.nextDate.isEmpty() ? r.nextDate : "") + (!r.nextDate.isEmpty() && r.nextKm > 0 ? " • " : "") + (r.nextKm > 0 ? formatInt(r.nextKm) + " km" : "");
            TextView n = tv(next, warning, 10, true);
            n.setPadding(0, dp(7), 0, 0);
            c.addView(n);
        }
        if (actions) {
            gap(c, 10);
            Button a = smallButton(r.attachment.isEmpty() ? "Fotoğraf / belge ekle" : "Fotoğraf / belgeyi aç");
            a.setOnClickListener(v -> { if (r.attachment.isEmpty()) pickRecordAttachment(r.id); else openAttachment(r.attachment); });
            c.addView(a, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout cvLine(String name, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(7), 0, dp(7));
        r.addView(tv(name, text, 12, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(tv(value, accent, 12, true));
        return r;
    }

    private LinearLayout featureLine(String title, String sub) {
        LinearLayout r = card();
        r.setPadding(dp(15), dp(13), dp(15), dp(13));
        r.addView(tv(title, text, 14, true));
        r.addView(tv(sub, muted, 11, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        r.setLayoutParams(lp);
        return r;
    }

    private LinearLayout infoCard(String titleValue, String subtitle, int color) {
        LinearLayout b = card();
        b.setPadding(dp(15), dp(13), dp(15), dp(13));
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.addView(tv("●", color, 12, true), new LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(tv(titleValue, text, 13, true));
        texts.addView(tv(subtitle, muted, 11, false));
        line.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        b.addView(line);
        return b;
    }

    private String moduleIcon(String m) {
        if ("Bakım".equals(m)) return "B";
        if ("Hasar".equals(m)) return "H";
        if ("Ekspertiz".equals(m)) return "E";
        if ("Muayene".equals(m)) return "M";
        if ("Vergi".equals(m)) return "V";
        if ("Sigorta/Kasko".equals(m)) return "S";
        if ("Yakıt".equals(m)) return "Y";
        return "₺";
    }

    private int moduleColor(String m) {
        if ("Hasar".equals(m)) return danger;
        if ("Ekspertiz".equals(m)) return warning;
        if ("Muayene".equals(m)) return Color.rgb(56, 167, 142);
        if ("Vergi".equals(m)) return Color.rgb(230, 140, 60);
        if ("Sigorta/Kasko".equals(m)) return accent2;
        if ("Yakıt".equals(m)) return Color.rgb(34, 160, 210);
        if ("Giderler".equals(m)) return Color.rgb(93, 111, 147);
        return accent;
    }

    private String moduleTitle(String m) {
        if ("Bakım".equals(m)) return "Bakım & Onarım";
        if ("Sigorta/Kasko".equals(m)) return "Sigorta & Kasko";
        if ("Yakıt".equals(m)) return "Yakıt / Şarj";
        return m;
    }

    private String moduleSubtitle(String m) {
        if ("Bakım".equals(m)) return "Bakım, değişen parçalar ve sonraki işlem";
        if ("Hasar".equals(m)) return "Hasar, onarım, fotoğraf ve belgeler";
        if ("Ekspertiz".equals(m)) return "Ekspertiz raporları ve sonuçları";
        if ("Muayene".equals(m)) return "Muayene sonucu ve sonraki tarih";
        if ("Vergi".equals(m)) return "Vergi dönemleri, ödemeler ve makbuzlar";
        if ("Sigorta/Kasko".equals(m)) return "Poliçe dönemleri ve belgeler";
        if ("Yakıt".equals(m)) return "Yakıt veya elektrik şarj kayıtları";
        return "Otopark, yıkama, geçiş ve diğer giderler";
    }

    private String moduleInfoTitle(String m) {
        if ("Bakım".equals(m)) return "Tek bakım ekranı";
        if ("Hasar".equals(m)) return "Hasar kendi geçmişinde";
        if ("Ekspertiz".equals(m)) return "Raporları bu bölümde tut";
        return moduleTitle(m) + " geçmişi";
    }

    private String moduleInfo(String m) {
        if ("Bakım".equals(m)) return "Yağ, genel bakım veya onarım gibi işlemleri tek kayıtta tut. Değişen parçayı listeden seçebilir veya açıklamaya kendin yazabilirsin.";
        if ("Hasar".equals(m)) return "Hasarın tarihini, kilometreyi, maliyeti, açıklamayı ve fotoğraf / belgeyi tek kayıtta sakla.";
        if ("Ekspertiz".equals(m)) return "Firma, tarih, kilometre ve ekspertiz sonucunu kaydet; rapor fotoğrafı veya PDF ekle.";
        if ("Muayene".equals(m)) return "Muayene sonucunu ve sonraki muayene tarihini sakla; 7 gün ve 1 gün kala hatırlatma al.";
        if ("Vergi".equals(m)) return "Vergi türü, dönem, ödeme ve belge bilgilerini sakla. CV kişisel bilgileri bu bölümden alınmaz.";
        if ("Sigorta/Kasko".equals(m)) return "Sigorta veya kasko dönemini, şirketi, tutarı ve belgeyi kaydet.";
        return "Kayıtlarını tarih ve kilometreyle düzenli şekilde takip et.";
    }

    private String recordTitleHint(String m) {
        if ("Bakım".equals(m)) return "Bakım / onarım başlığı";
        if ("Hasar".equals(m)) return "Hasar / onarım başlığı";
        if ("Ekspertiz".equals(m)) return "Ekspertiz firması / rapor başlığı";
        if ("Muayene".equals(m)) return "Muayene sonucu / başlık";
        if ("Vergi".equals(m)) return "Vergi türü / dönem";
        if ("Sigorta/Kasko".equals(m)) return "Şirket / poliçe türü";
        return "Kayıt başlığı";
    }

    private String defaultTitle(String m) {
        if ("Bakım".equals(m)) return "Periyodik bakım";
        if ("Muayene".equals(m)) return "Periyodik muayene";
        return "";
    }

    private String recordDetailHint(String m) {
        if ("Bakım".equals(m)) return "Açıklama / diğer değişen parçalar";
        if ("Hasar".equals(m)) return "Hasar açıklaması, onarım, değişen veya boyanan parçalar";
        if ("Ekspertiz".equals(m)) return "Ekspertiz sonucu / notlar";
        if ("Muayene".equals(m)) return "Kusurlar / açıklama";
        if ("Vergi".equals(m)) return "Ödeme / dönem açıklaması";
        if ("Sigorta/Kasko".equals(m)) return "Kapsam / açıklama";
        return "Açıklama";
    }

    private CheckBox checkbox(String textValue, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(textValue);
        c.setChecked(checked);
        c.setTextSize(13);
        c.setPadding(0, dp(3), 0, dp(3));
        return c;
    }

    private void sectionTitle(LinearLayout p, String title, String sub) {
        p.addView(tv(title, text, 18, true));
        TextView s = tv(sub, muted, 11, false);
        s.setPadding(0, dp(2), 0, dp(10));
        p.addView(s);
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(cardDrawable(surface, dp(20), stroke));
        return v;
    }

    private GradientDrawable cardDrawable(int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeColor != Color.TRANSPARENT) d.setStroke(dp(1), strokeColor);
        return d;
    }

    private TextView tv(String value, int color, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setLineSpacing(0, 1.08f);
        return t;
    }

    private TextView label(String value) {
        TextView t = tv(value, muted, 11, true);
        t.setPadding(0, dp(8), 0, dp(3));
        return t;
    }

    private Button primaryButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{accent, accent2});
        g.setCornerRadius(dp(16));
        b.setBackground(g);
        b.setMinHeight(dp(52));
        return b;
    }

    private Button smallButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(text);
        b.setBackground(cardDrawable(surface2, dp(14), Color.TRANSPARENT));
        return b;
    }

    private LinearLayout formLayout() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(18), dp(4), dp(18), dp(6));
        return f;
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(false);
        e.setTextSize(14);
        e.setPadding(dp(10), dp(10), dp(10), dp(10));
        return e;
    }

    private EditText numberField(String hint, String value) {
        EditText e = field(hint, value);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    private EditText decimalField(String hint, String value) {
        EditText e = field(hint, value);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    private void gap(LinearLayout p, int h) { p.addView(new Space(this), new LinearLayout.LayoutParams(1, dp(h))); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private String today() { return new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date()); }
    private int safeInt(String s, int fallback) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; } }
    private double safeDouble(String s) { try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (Exception e) { return 0; } }
    private String formatInt(int v) { return NumberFormat.getIntegerInstance(new Locale("tr", "TR")).format(v); }
    private String formatMoney(double v) { NumberFormat nf = NumberFormat.getNumberInstance(new Locale("tr", "TR")); nf.setMaximumFractionDigits(2); return nf.format(v) + " ₺"; }

    static class CvOptions {
        boolean showPlate, showCosts, includeDamage, includeExpertise, includeOfficial, includeGallery;
        String phone = "", note = "";
    }

    class PdfWriter {
        final android.graphics.pdf.PdfDocument doc;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        android.graphics.pdf.PdfDocument.Page page;
        Canvas canvas;
        int pageNo = 0;
        int y = 0;
        final int width = 595, height = 842, left = 42, right = 553;

        PdfWriter(android.graphics.pdf.PdfDocument d) { doc = d; }

        void newPage() {
            if (page != null) doc.finishPage(page);
            pageNo++;
            page = doc.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(width, height, pageNo).create());
            canvas = page.getCanvas();
            y = 48;
            paint.setColor(Color.rgb(47, 111, 237));
            canvas.drawRoundRect(new RectF(30, 22, 565, 30), 4, 4, paint);
        }

        void ensure(int need) { if (y + need > 790) newPage(); }
        void finishPage() { if (page != null) { doc.finishPage(page); page = null; } }
        void gap(int h) { y += h; }

        void title(String s) {
            ensure(45);
            paint.setColor(Color.rgb(47, 111, 237));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(23);
            canvas.drawText(s, left, y, paint);
            y += 38;
        }

        void big(String s) {
            ensure(34);
            paint.setColor(Color.BLACK);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(21);
            canvas.drawText(s, left, y, paint);
            y += 28;
        }

        void small(String s) {
            ensure(18);
            paint.setColor(Color.DKGRAY);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(10);
            canvas.drawText(s, left, y, paint);
            y += 15;
        }

        void section(String s) {
            ensure(40);
            y += 10;
            paint.setColor(Color.rgb(238, 242, 249));
            canvas.drawRoundRect(new RectF(left, y - 17, right, y + 8), 8, 8, paint);
            paint.setColor(Color.rgb(47, 111, 237));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(12);
            canvas.drawText(s, left + 10, y, paint);
            y += 25;
        }

        void kv(String k, String v) {
            ensure(22);
            paint.setTextSize(10);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setColor(Color.DKGRAY);
            canvas.drawText(k, left, y, paint);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setColor(Color.BLACK);
            canvas.drawText(v, 315, y, paint);
            y += 18;
        }

        void paragraph(String s) {
            List<String> lines = wrap(s, 84);
            ensure(lines.size() * 15 + 8);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(10);
            paint.setColor(Color.DKGRAY);
            for (String line : lines) { canvas.drawText(line, left, y, paint); y += 14; }
            y += 5;
        }

        void record(MainActivity.Record r, boolean showCosts) {
            ensure(64);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(10.5f);
            paint.setColor(Color.BLACK);
            canvas.drawText(r.type + " • " + r.title, left, y, paint);
            y += 15;
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(9.5f);
            paint.setColor(Color.DKGRAY);
            String meta = r.date + "   |   " + formatInt(r.km) + " km" + (showCosts && r.cost > 0 ? "   |   " + formatMoney(r.cost) : "");
            canvas.drawText(meta, left, y, paint);
            y += 14;
            if (r.detail != null && !r.detail.isEmpty()) paragraph(r.detail);
            y += 4;
        }

        void photo(Bitmap bitmap) {
            if (bitmap == null) return;
            ensure(190);
            int maxW = right - left;
            int maxH = 170;
            float scale = Math.min((float) maxW / bitmap.getWidth(), (float) maxH / bitmap.getHeight());
            int w = Math.max(1, (int) (bitmap.getWidth() * scale));
            int h = Math.max(1, (int) (bitmap.getHeight() * scale));
            float x = left + (maxW - w) / 2f;
            canvas.drawBitmap(bitmap, null, new RectF(x, y, x + w, y + h), paint);
            y += h + 12;
        }

        List<String> wrap(String textValue, int maxChars) {
            List<String> out = new ArrayList<>();
            if (textValue == null || textValue.isEmpty()) return out;
            String[] words = textValue.replace('\n', ' ').split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (line.length() > 0 && line.length() + word.length() + 1 > maxChars) {
                    out.add(line.toString());
                    line = new StringBuilder();
                }
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
            if (line.length() > 0) out.add(line.toString());
            return out;
        }
    }
}
