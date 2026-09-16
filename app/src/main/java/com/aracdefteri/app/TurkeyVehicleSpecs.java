package com.aracdefteri.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Türkiye kataloğu için teknik varyant katmanı.
 * Marka/model seçildikten sonra paket-motor-vites-yakıt-kasa bilgisini otomatik doldurur.
 * Bu sınıf fiyat tutmaz; teknik katalog bilgisidir ve zamanla genişletilir.
 */
public final class TurkeyVehicleSpecs {
    private TurkeyVehicleSpecs() {}

    public static final class Spec {
        public final int fromYear, toYear;
        public final String variant, generation, trim, body, fuel, transmission, engine, power, drivetrain;

        Spec(int fromYear, int toYear, String variant, String generation, String trim,
             String body, String fuel, String transmission, String engine, String power, String drivetrain) {
            this.fromYear = fromYear;
            this.toYear = toYear;
            this.variant = variant;
            this.generation = generation;
            this.trim = trim;
            this.body = body;
            this.fuel = fuel;
            this.transmission = transmission;
            this.engine = engine;
            this.power = power;
            this.drivetrain = drivetrain;
        }
    }

    private static final Map<String, List<Spec>> DATA = new LinkedHashMap<>();

    static {
        // PEUGEOT - güncel Türkiye teknik broşürlerinden çekirdek varyantlar.
        add("Peugeot", "Traveller", 2025, 2026, "2.0 BlueHDi 180 hp EAT8", "K0 makyajlı", "Traveller", "Minivan", "Dizel", "Otomatik", "2.0 BlueHDi", "180 hp", "Önden çekiş");
        add("Peugeot", "Traveller", 2026, 2026, "2.2 BlueHDi 180 hp EAT8", "K0 makyajlı", "Traveller", "Minivan", "Dizel", "Otomatik", "2.2 BlueHDi", "180 hp", "Önden çekiş");
        add("Peugeot", "Partner", 2025, 2026, "1.5 BlueHDi 100 hp 6MT", "K9", "Van", "Panelvan", "Dizel", "Manuel", "1.5 BlueHDi", "100 hp", "Önden çekiş");
        add("Peugeot", "Partner", 2025, 2026, "1.5 BlueHDi 130 hp 6MT", "K9", "Van", "Panelvan", "Dizel", "Manuel", "1.5 BlueHDi", "130 hp", "Önden çekiş");
        add("Peugeot", "Partner", 2025, 2026, "1.5 BlueHDi 130 hp EAT8", "K9", "Van", "Panelvan", "Dizel", "Otomatik", "1.5 BlueHDi", "130 hp", "Önden çekiş");
        add("Peugeot", "Rifter", 2024, 2026, "1.5 BlueHDi 130 hp EAT8", "K9", "GT", "MPV", "Dizel", "Otomatik", "1.5 BlueHDi", "130 hp", "Önden çekiş");
        add("Peugeot", "2008", 2024, 2026, "1.2 PureTech 130 EAT8", "II makyajlı", "Allure", "SUV", "Benzin", "Otomatik", "1.2 PureTech", "130 hp", "Önden çekiş");
        add("Peugeot", "3008", 2025, 2026, "1.2 Hybrid 145 e-DCS6", "III", "Allure", "SUV", "Hibrit / Benzin", "DCT / EDC / DSG", "1.2 Hybrid", "145 hp", "Önden çekiş");
        add("Peugeot", "3008", 2025, 2026, "1.2 Hybrid 145 e-DCS6 GT", "III", "GT", "SUV", "Hibrit / Benzin", "DCT / EDC / DSG", "1.2 Hybrid", "145 hp", "Önden çekiş");

        // TOYOTA COROLLA E210 - Türkiye hibrit çekirdek varyantları.
        add("Toyota", "Corolla Hybrid", 2019, 2022, "1.8 Hybrid 122 HP e-CVT", "E210", "", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "122 hp", "4X2");
        add("Toyota", "Corolla Hybrid", 2019, 2022, "1.8 Hybrid 122 HP e-CVT • Dream", "E210", "Dream", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "122 hp", "4X2");
        add("Toyota", "Corolla Hybrid", 2019, 2022, "1.8 Hybrid 122 HP e-CVT • Flame", "E210", "Flame", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "122 hp", "4X2");
        add("Toyota", "Corolla Hybrid", 2019, 2022, "1.8 Hybrid 122 HP e-CVT • Passion", "E210", "Passion", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "122 hp", "4X2");
        add("Toyota", "Corolla Hybrid", 2023, 2025, "1.8 Hybrid 140 HP e-CVT", "E210 makyajlı", "", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "140 hp", "4X2");
        add("Toyota", "Corolla", 2019, 2022, "1.8 Hybrid 122 HP e-CVT", "E210", "", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "122 hp", "4X2");
        add("Toyota", "Corolla", 2023, 2025, "1.8 Hybrid 140 HP e-CVT", "E210 makyajlı", "", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "140 hp", "4X2");

        // TOYOTA COROLLA 2026 Türkiye.
        add("Toyota", "Corolla", 2026, 2026, "1.5 Benzin 125 HP Multidrive S • Vision Plus", "E210", "Vision Plus", "Sedan", "Benzin", "CVT / e-CVT", "1.5 L Benzin", "125 hp", "4X2");
        add("Toyota", "Corolla", 2026, 2026, "1.5 Benzin 125 HP Multidrive S • Dream", "E210", "Dream", "Sedan", "Benzin", "CVT / e-CVT", "1.5 L Benzin", "125 hp", "4X2");
        add("Toyota", "Corolla", 2026, 2026, "1.5 Benzin 125 HP Multidrive S • Dream X-Pack", "E210", "Dream X-Pack", "Sedan", "Benzin", "CVT / e-CVT", "1.5 L Benzin", "125 hp", "4X2");
        add("Toyota", "Corolla", 2026, 2026, "1.5 Benzin 125 HP Multidrive S • Flame X-Pack", "E210", "Flame X-Pack", "Sedan", "Benzin", "CVT / e-CVT", "1.5 L Benzin", "125 hp", "4X2");
        add("Toyota", "Corolla", 2026, 2026, "1.5 Benzin 125 HP Multidrive S • Passion X-Pack", "E210", "Passion X-Pack", "Sedan", "Benzin", "CVT / e-CVT", "1.5 L Benzin", "125 hp", "4X2");
        add("Toyota", "Corolla", 2026, 2026, "1.8 Hybrid 140 HP e-CVT • Hybrid Dream", "E210", "Hybrid Dream", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "140 hp", "4X2");
        add("Toyota", "Corolla", 2026, 2026, "1.8 Hybrid 140 HP e-CVT • Hybrid Dream X-Pack", "E210", "Hybrid Dream X-Pack", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "140 hp", "4X2");
        add("Toyota", "Corolla", 2026, 2026, "1.8 Hybrid 140 HP e-CVT • Hybrid Flame X-Pack", "E210", "Hybrid Flame X-Pack", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "140 hp", "4X2");
        add("Toyota", "Corolla", 2026, 2026, "1.8 Hybrid 140 HP e-CVT • Hybrid Passion X-Pack", "E210", "Hybrid Passion X-Pack", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "140 hp", "4X2");
        add("Toyota", "Corolla Sedan", 2026, 2026, "1.8 Hybrid 140 HP e-CVT • Hybrid Passion X-Pack", "E210", "Hybrid Passion X-Pack", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "140 hp", "4X2");
        add("Toyota", "Corolla Cross", 2025, 2026, "1.8 Hybrid e-CVT • Passion X-Pack", "XG10", "Passion X-Pack", "SUV", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "140 hp", "4X2");
        add("Toyota", "C-HR", 2024, 2026, "1.8 Hybrid e-CVT • Passion", "II", "Passion", "SUV", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "140 hp", "4X2");

        // RENAULT CLIO 2026 Türkiye.
        add("Renault", "Clio", 2026, 2026, "TCe EDC 115 hp • evolution plus", "VI", "evolution plus", "Hatchback", "Benzin", "DCT / EDC / DSG", "TCe EDC 115", "115 hp", "Önden çekiş");
        add("Renault", "Clio", 2026, 2026, "TCe EDC 115 hp • esprit Alpine", "VI", "esprit Alpine", "Hatchback", "Benzin", "DCT / EDC / DSG", "TCe EDC 115", "115 hp", "Önden çekiş");
        add("Renault", "Taliant", 2024, 2026, "1.0 Turbo X-Tronic • Touch", "I", "Touch", "Sedan", "Benzin", "CVT / e-CVT", "1.0 Turbo", "90 hp", "Önden çekiş");
        add("Renault", "Megane Sedan", 2024, 2026, "1.3 TCe EDC • Touch", "IV", "Touch", "Sedan", "Benzin", "DCT / EDC / DSG", "1.3 TCe", "140 hp", "Önden çekiş");
        add("Renault", "Austral", 2024, 2026, "E-Tech full hybrid 200 • techno esprit Alpine", "I", "techno esprit Alpine", "SUV", "Hibrit / Benzin", "Otomatik", "E-Tech full hybrid", "200 hp", "Önden çekiş");

        // FIAT - Türkiye'de yaygın Egea/Doblo/Fiorino.
        add("Fiat", "Egea Sedan", 2024, 2026, "1.4 Fire 95 HP • Easy", "356", "Easy", "Sedan", "Benzin", "Manuel", "1.4 Fire", "95 hp", "Önden çekiş");
        add("Fiat", "Egea Sedan", 2024, 2026, "1.6 Multijet 130 HP DCT • Urban", "356", "Urban", "Sedan", "Dizel", "DCT / EDC / DSG", "1.6 Multijet", "130 hp", "Önden çekiş");
        add("Fiat", "Egea Cross", 2024, 2026, "1.6 Multijet 130 HP DCT • Lounge", "356", "Lounge", "Crossover", "Dizel", "DCT / EDC / DSG", "1.6 Multijet", "130 hp", "Önden çekiş");
        add("Fiat", "Fiorino", 2024, 2026, "1.3 Multijet 95 HP • Premio", "225", "Premio", "Panelvan", "Dizel", "Manuel", "1.3 Multijet", "95 hp", "Önden çekiş");
        add("Fiat", "Doblo", 2024, 2026, "1.5 BlueHDi 130 HP EAT8", "K9", "Combi", "Combi", "Dizel", "Otomatik", "1.5 BlueHDi", "130 hp", "Önden çekiş");

        // VOLKSWAGEN.
        add("Volkswagen", "Golf", 2025, 2026, "1.5 eTSI 150 PS DSG • Life", "VIII makyajlı", "Life", "Hatchback", "Hibrit / Benzin", "DCT / EDC / DSG", "1.5 eTSI", "150 hp", "Önden çekiş");
        add("Volkswagen", "Golf", 2025, 2026, "1.5 eTSI 150 PS DSG • Style", "VIII makyajlı", "Style", "Hatchback", "Hibrit / Benzin", "DCT / EDC / DSG", "1.5 eTSI", "150 hp", "Önden çekiş");
        add("Volkswagen", "Polo", 2024, 2026, "1.0 TSI 95 PS DSG • Life", "VI makyajlı", "Life", "Hatchback", "Benzin", "DCT / EDC / DSG", "1.0 TSI", "95 hp", "Önden çekiş");
        add("Volkswagen", "T-Roc", 2024, 2026, "1.5 TSI 150 PS DSG • Style", "I makyajlı", "Style", "SUV", "Benzin", "DCT / EDC / DSG", "1.5 TSI", "150 hp", "Önden çekiş");
        add("Volkswagen", "ID.4", 2024, 2026, "Pro", "I", "Pro", "SUV", "Elektrik", "Tek oranlı elektrikli", "Elektrik motoru", "286 hp", "Arkadan çekiş");

        // FORD / HYUNDAI / HONDA / SKODA / OPEL - yaygın örnekler.
        add("Ford", "Focus", 2024, 2026, "1.0 EcoBoost Hybrid 125 PS 7DCT • Titanium", "IV makyajlı", "Titanium", "Hatchback", "Hibrit / Benzin", "DCT / EDC / DSG", "1.0 EcoBoost Hybrid", "125 hp", "Önden çekiş");
        add("Ford", "Puma", 2024, 2026, "1.0 EcoBoost Hybrid 125 PS 7DCT • Titanium", "I makyajlı", "Titanium", "SUV", "Hibrit / Benzin", "DCT / EDC / DSG", "1.0 EcoBoost Hybrid", "125 hp", "Önden çekiş");
        add("Hyundai", "i20", 2024, 2026, "1.0 T-GDI 100 PS 7DCT • Elite", "III makyajlı", "Elite", "Hatchback", "Benzin", "DCT / EDC / DSG", "1.0 T-GDI", "100 hp", "Önden çekiş");
        add("Hyundai", "Bayon", 2024, 2026, "1.0 T-GDI 100 PS 7DCT • Elite", "I makyajlı", "Elite", "SUV", "Benzin", "DCT / EDC / DSG", "1.0 T-GDI", "100 hp", "Önden çekiş");
        add("Honda", "Civic", 2024, 2026, "1.5 VTEC Turbo CVT • Executive+", "FE", "Executive+", "Sedan", "Benzin", "CVT / e-CVT", "1.5 VTEC Turbo", "182 hp", "Önden çekiş");
        add("Skoda", "Octavia", 2025, 2026, "1.5 eTSI 150 PS DSG • Premium", "IV makyajlı", "Premium", "Sedan", "Hibrit / Benzin", "DCT / EDC / DSG", "1.5 eTSI", "150 hp", "Önden çekiş");
        add("Opel", "Corsa", 2024, 2026, "1.2 Turbo 100 HP AT8 • GS", "F makyajlı", "GS", "Hatchback", "Benzin", "Otomatik", "1.2 Turbo", "100 hp", "Önden çekiş");

        // Elektrikli araçlar.
        add("Togg", "T10X", 2024, 2026, "V1 RWD Standart Menzil", "T10X", "V1", "SUV", "Elektrik", "Tek oranlı elektrikli", "Elektrik motoru", "218 hp", "Arkadan çekiş");
        add("Togg", "T10X", 2024, 2026, "V2 RWD Uzun Menzil", "T10X", "V2", "SUV", "Elektrik", "Tek oranlı elektrikli", "Elektrik motoru", "218 hp", "Arkadan çekiş");
        add("Tesla", "Model Y", 2024, 2026, "RWD", "Juniper / güncel", "RWD", "SUV", "Elektrik", "Tek oranlı elektrikli", "Elektrik motoru", "", "Arkadan çekiş");
        add("BYD", "Atto 3", 2024, 2026, "Design", "Atto 3", "Design", "SUV", "Elektrik", "Tek oranlı elektrikli", "Elektrik motoru", "204 hp", "Önden çekiş");
        add("MG", "MG4", 2024, 2026, "Luxury", "MG4", "Luxury", "Hatchback", "Elektrik", "Tek oranlı elektrikli", "Elektrik motoru", "204 hp", "Arkadan çekiş");
    }

    private static void add(String brand, String model, int fromYear, int toYear, String variant,
                            String generation, String trim, String body, String fuel,
                            String transmission, String engine, String power, String drivetrain) {
        String key = key(brand, model);
        List<Spec> list = DATA.get(key);
        if (list == null) {
            list = new ArrayList<>();
            DATA.put(key, list);
        }
        list.add(new Spec(fromYear, toYear, variant, generation, trim, body, fuel, transmission, engine, power, drivetrain));
    }

    private static String key(String brand, String model) {
        return norm(brand) + "|" + norm(model);
    }

    private static String norm(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(new Locale("tr", "TR"));
    }

    public static List<Spec> specsFor(String brand, String model, int year) {
        boolean hybridOnly=norm(brand).equals("toyota")&&norm(model).equals("corolla hybrid")&&year>=2026;
        if(norm(brand).equals("toyota")&&(norm(model).equals("corolla sedan")||hybridOnly))model="Corolla";
        List<Spec> current=TurkeyVehicleSpecs2026.get(brand,model,year);
        if(current!=null)return current;
        ArrayList<Spec> out = new ArrayList<>();
        List<Spec> list = DATA.get(key(brand, model));
        if (list != null) {
            for (Spec s : list) if (year >= s.fromYear && year <= s.toYear) out.add(s);
        }
        out.addAll(TurkeyVehicleSpecsExtra.specsFor(brand, model, year));
        final boolean filterHybrid=hybridOnly;
        if(filterHybrid)out.removeIf(s -> !s.fuel.startsWith("Hibrit"));
        LinkedHashMap<String,Spec> unique=new LinkedHashMap<>();
        for(Spec s:out)unique.put(s.variant,s);
        out=new ArrayList<>(unique.values());
        return out;
    }

    public static String[] variantLabels(String brand, String model, int year) {
        List<Spec> specs = specsFor(brand, model, year);
        if (specs.isEmpty()) return new String[]{"Teknik bilgiyi manuel gir"};
        ArrayList<String> labels = new ArrayList<>();
        for (Spec s : specs) labels.add(s.variant);
        labels.add("Teknik bilgiyi manuel gir");
        return labels.toArray(new String[0]);
    }

    public static Spec find(String brand, String model, int year, String variant) {
        if (variant == null || variant.startsWith("Teknik bilgiyi")) return null;
        for (Spec s : specsFor(brand, model, year)) if (s.variant.equals(variant)) return s;
        return null;
    }

    /** Desteklenmeyen modelde en azından model adından güvenli bir temel çıkarım yapar. */
    public static Spec inferred(String type, String brand, String model, int year) {
        String m = norm(model);
        String body = "";
        if (TurkeyVehicleCatalog.TYPE_SUV.equals(type)) body = "SUV";
        else if (TurkeyVehicleCatalog.TYPE_LCV.equals(type)) body = "Panelvan";
        else if (TurkeyVehicleCatalog.TYPE_PICKUP.equals(type)) body = "Çift Kabin";
        else if (TurkeyVehicleCatalog.TYPE_CAR.equals(type)) body = "Sedan";

        String fuel = "";
        String trans = "";
        String engine = "";
        String power = "";
        String drive = "";
        if (m.contains("corolla") && m.contains("hybrid")) {
            body = "Sedan";
            fuel = "Hibrit / Benzin";
            trans = "CVT / e-CVT";
            engine = "1.8 L Hybrid";
            power = year <= 2022 ? "122 hp" : "140 hp";
            drive = "4X2";
        }
        if (m.contains("id.") || m.contains("model y") || m.contains("model 3") || m.contains("t10x") || m.contains("t10f") || m.contains("ioniq") || m.contains("leaf") || m.contains("zoe") || m.contains("spring") || m.contains("mg4") || m.contains("atto 3") || m.contains("bz4x") || m.contains("enyaq") || m.contains("eqa") || m.contains("eqb") || m.contains("eqe") || m.contains("eqs")) {
            fuel = "Elektrik";
            trans = "Tek oranlı elektrikli";
            engine = "Elektrik motoru";
        }
        if (m.contains("cross") || m.contains("c-hr") || m.contains("rav4")) body = "SUV";
        if (m.contains("hatchback") || m.equals("golf") || m.equals("polo") || m.equals("clio") || m.equals("i20") || m.equals("corsa") || m.equals("fabia")) body = "Hatchback";
        return new Spec(year, year, "Otomatik temel bilgi", "", "", body, fuel, trans, engine, power, drive);
    }
}

