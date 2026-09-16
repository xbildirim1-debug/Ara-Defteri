from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"Patch marker not found: {label}")
    return text.replace(old, new, 1)

rp_path = ROOT / "app/src/main/java/com/aracdefteri/app/RecordParser.java"
rp = rp_path.read_text(encoding="utf-8")
if "public String documentKind" not in rp:
    rp = replace_once(
        rp,
        '''        public int km = 0;
        public final Set<String> maintenanceParts = new LinkedHashSet<>();
''',
        '''        public int km = 0;

        // Belge türünden bağımsız ortak alanlar.
        public String documentKind = "";
        public String plate = "";
        public String policyNo = "";
        public String policyStartDate = "";
        public String policyEndDate = "";
        public String inspectionResult = "";
        public String nextDate = "";
        public String detailSummary = "";
        public String expertiseSubtype = "";
        public String taxSubtype = "";
        public String paymentPeriod = "";
        public int confidence = 0;
        public final List<String> warnings = new ArrayList<>();

        public final Set<String> maintenanceParts = new LinkedHashSet<>();
''',
        "RecordParser Parsed fields"
    )

if "SmartDocumentAnalyzer.enrich(raw, p);" not in rp:
    rp = replace_once(
        rp,
        '''        detectMaintenanceParts(lower, p.maintenanceParts);

        // Matematiksel tutarlılık''',
        '''        detectMaintenanceParts(lower, p.maintenanceParts);

        // Farklı fiş/poliçe/muayene/ekspertiz şablonları için belgeye özel ikinci katman.
        SmartDocumentAnalyzer.enrich(raw, p);

        // Matematiksel tutarlılık''',
        "RecordParser analyzer call"
    )
rp_path.write_text(rp, encoding="utf-8")

ui_path = ROOT / "app/src/main/java/com/aracdefteri/app/NextMainActivity.java"
ui = ui_path.read_text(encoding="utf-8")

ui = ui.replace(
    'TextView subtitle = tv("Fiş/belge fotoğrafını okut veya kaydı konuş. Bulunan alanlar forma gelir; otomatik kaydedilmez.", muted, 10, false);',
    'TextView subtitle = tv("Fiş, poliçe, muayene/ekspertiz fotoğrafı veya PDF okut; ya da kaydı konuş. Bulunan alanlar forma gelir, otomatik kaydedilmez.", muted, 10, false);',
    1
)

old_picker = '''    private void pickOcrImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(i, "Fiş / belge fotoğrafı seç"), PICK_OCR_IMAGE);
    }
'''
new_picker = '''    private void pickOcrImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(i, "Fiş / belge fotoğrafı veya PDF seç"), PICK_OCR_IMAGE);
    }
'''
if old_picker in ui:
    ui = ui.replace(old_picker, new_picker, 1)

ui = ui.replace(
    'pendingSmartForm.smartStatus.setText("Fotoğraf cihaz üzerinde okunuyor…");',
    'pendingSmartForm.smartStatus.setText("Belge cihaz üzerinde okunuyor…");',
    1
)

generic_marker = '''        if (p.quantity > 0 && f.quantity != null) {
            f.quantity.setText(trimDouble(p.quantity));
            markSmartField(f.quantity);
            found++;
        }

        if ("Yakıt".equals(module)) {
'''
generic_new = '''        if (p.quantity > 0 && f.quantity != null) {
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
'''
if generic_marker in ui:
    ui = ui.replace(generic_marker, generic_new, 1)

maint_marker = '''            if (p.vendor != null && !p.vendor.isEmpty()) {
                if (f.title != null) { f.title.setText(p.vendor + " bakım"); markSmartField(f.title); }
                if (f.detail != null && f.detail.getText().toString().trim().isEmpty()) {
                    f.detail.setText("Servis: " + p.vendor);
                    markSmartField(f.detail);
                }
                found++;
            }
        } else if ("Sigorta/Kasko".equals(module)) {
'''
maint_new = '''            if (p.vendor != null && !p.vendor.isEmpty()) {
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
'''
if maint_marker in ui:
    ui = ui.replace(maint_marker, maint_new, 1)

insurance_old = '''            if (p.vendor != null && !p.vendor.isEmpty() && f.detail != null) {
                f.detail.setText(p.vendor);
                markSmartField(f.detail);
                found++;
            }
        } else if ("Vergi".equals(module)) {
            if (p.type != null && "Vergi".equals(p.type) && f.subtype != null) {
                selectSpinnerValue(f.subtype, "MTV");
                markSmartSpinner(f.subtype);
            }
        } else if ("Hasar".equals(module) && p.type != null && "Hasar".equals(p.type) && f.subtype != null) {
            selectSpinnerValue(f.subtype, "Kaza");
            markSmartSpinner(f.subtype);
        }
'''
insurance_new = '''            if (f.detail != null) {
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
'''
if insurance_old in ui:
    ui = ui.replace(insurance_old, insurance_new, 1)
elif 'else if ("Muayene".equals(module))' not in ui:
    raise SystemExit("Patch marker not found: smart module branches")

summary_marker = '''        } else if ("Bakım".equals(module) || "Sigorta/Kasko".equals(module)) {
            items.add("Firma " + ((p.vendor != null && !p.vendor.isEmpty()) ? "✓" : "okunamadı"));
        }
        return source + " sonucu • " + join(items, " • ") + "\\nOkunamayan alanları manuel gir; otomatik kayıt yapılmadı.";
'''
summary_new = '''        } else if ("Bakım".equals(module) || "Sigorta/Kasko".equals(module)) {
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
        return source + " sonucu • " + join(items, " • ") + "\\nOkunamayan alanları manuel gir; otomatik kayıt yapılmadı.";
'''
if summary_marker in ui:
    ui = ui.replace(summary_marker, summary_new, 1)

save_marker = '''        if ("Yakıt".equals(module)) {
            r.quantity = f.quantity == null ? 0 : safeDouble(f.quantity.getText().toString());
            r.unit = "Elektrik".equals(r.subtype) ? "kWh" : "L";
            r.unitPrice = r.quantity > 0 && r.cost > 0 ? r.cost / r.quantity : 0;
            r.status = f.fullTank != null && f.fullTank.isChecked() ? "full" : "partial";
            r.extra = "";
            if (r.quantity <= 0) { toast("Yakıt miktarını girmelisin"); return; }
            if (r.cost <= 0) { toast("Toplam ödenen tutarı girmelisin"); return; }
        }

        if (editing) {
'''
save_new = '''        if ("Yakıt".equals(module)) {
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
'''
if save_marker in ui:
    ui = ui.replace(save_marker, save_new, 1)

ui_path.write_text(ui, encoding="utf-8")
print("Smart document upgrade applied.")
