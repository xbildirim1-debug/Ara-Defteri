package com.aracdefteri.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Türkiye odaklı yerel araç kataloğu v1.
 *
 * Amaç: uygulamanın dış API'ye bağlı olmadan Araç türü -> Marka -> Model akışını
 * çalıştırması. Bu ilk seed, Türkiye'de yaygın binek/SUV/hafif ticari/pick-up
 * modellerini kapsar. Katalog bilinçli olarak fiyat/kasko değeri tutmaz.
 *
 * Kaynak yaklaşımı: TSB/GİB marka-model kapsamı referans alınır; teknik alanlar
 * üretici verisiyle ayrı sürümlerde zenginleştirilecektir. Kullanıcının aracı
 * listede yoksa manuel giriş daima açıktır.
 */
public final class TurkeyVehicleCatalog {
    private TurkeyVehicleCatalog() {}

    public static final String TYPE_CAR = "Otomobil";
    public static final String TYPE_SUV = "SUV / Crossover";
    public static final String TYPE_LCV = "Hafif Ticari";
    public static final String TYPE_PICKUP = "Pick-up";
    public static final String TYPE_COMMERCIAL = "Ticari";
    public static final String TYPE_MOTORCYCLE = "Motosiklet";

    private static final Map<String, LinkedHashMap<String, List<String>>> DATA = new LinkedHashMap<>();

    static {
        // Otomobil
        add(TYPE_CAR, "Renault", "Clio", "Megane", "Megane Sedan", "Symbol", "Taliant", "Fluence", "Latitude", "Laguna", "Talisman", "Zoe");
        add(TYPE_CAR, "Fiat", "Egea Sedan", "Egea Hatchback", "Linea", "Punto", "Grande Punto", "500", "500L", "Panda", "Bravo", "Palio", "Albea", "Marea", "Tempra", "Tipo");
        add(TYPE_CAR, "Tofaş", "Şahin", "Doğan", "Doğan SLX", "Kartal", "Serçe", "Murat 124", "Murat 131");
        add(TYPE_CAR, "Volkswagen", "Polo", "Golf", "Passat", "Jetta", "Bora", "Vento", "Arteon", "CC", "Scirocco", "Beetle", "ID.3");
        add(TYPE_CAR, "Ford", "Fiesta", "Focus", "Mondeo", "Escort", "Taunus", "Fusion", "Mustang");
        add(TYPE_CAR, "Toyota", "Corolla", "Corolla Sedan", "Auris", "Yaris", "Avensis", "Camry", "Prius", "Aygo", "Supra", "Celica");
        add(TYPE_CAR, "Hyundai", "i10", "i20", "i30", "Accent", "Accent Era", "Accent Blue", "Elantra", "Getz", "Sonata", "Ioniq");
        add(TYPE_CAR, "Peugeot", "106", "206", "207", "208", "301", "306", "307", "308", "408", "406", "407", "508", "RCZ");
        add(TYPE_CAR, "Citroën", "C1", "C2", "C3", "C4", "C5", "C-Elysee", "Xsara", "Xantia", "Saxo", "AMI");
        add(TYPE_CAR, "Opel", "Corsa", "Astra", "Vectra", "Insignia", "Omega", "Kadett", "Adam", "Tigra");
        add(TYPE_CAR, "Honda", "Civic", "Civic Sedan", "Accord", "City", "Jazz", "CR-Z", "Insight");
        add(TYPE_CAR, "Dacia", "Logan", "Sandero", "Sandero Stepway", "Spring");
        add(TYPE_CAR, "Skoda", "Fabia", "Octavia", "Superb", "Rapid", "Scala", "Favorit", "Felicia");
        add(TYPE_CAR, "SEAT", "Ibiza", "Leon", "Toledo", "Cordoba", "Exeo");
        add(TYPE_CAR, "Cupra", "Leon", "Born");
        add(TYPE_CAR, "Nissan", "Micra", "Almera", "Primera", "Pulsar", "Note", "Leaf", "Sunny");
        add(TYPE_CAR, "Kia", "Picanto", "Rio", "Ceed", "ProCeed", "Cerato", "Optima", "K5");
        add(TYPE_CAR, "Mercedes-Benz", "A Serisi", "B Serisi", "C Serisi", "CLA", "E Serisi", "CLS", "S Serisi", "EQE", "EQS", "190");
        add(TYPE_CAR, "BMW", "1 Serisi", "2 Serisi", "3 Serisi", "4 Serisi", "5 Serisi", "6 Serisi", "7 Serisi", "8 Serisi", "i3", "i4", "i5", "i7", "Z4");
        add(TYPE_CAR, "Audi", "A1", "A3", "A4", "A5", "A6", "A7", "A8", "TT", "e-tron GT", "80", "100");
        add(TYPE_CAR, "Volvo", "S40", "S60", "S80", "S90", "V40", "V60", "V90", "C30");
        add(TYPE_CAR, "Chevrolet", "Aveo", "Cruze", "Lacetti", "Kalos", "Spark", "Epica");
        add(TYPE_CAR, "Mazda", "Mazda 2", "Mazda 3", "Mazda 6", "MX-5", "RX-8");
        add(TYPE_CAR, "Mitsubishi", "Colt", "Lancer", "Carisma", "Galant");
        add(TYPE_CAR, "Suzuki", "Swift", "Baleno", "Celerio", "Splash");
        add(TYPE_CAR, "Subaru", "Impreza", "Legacy", "BRZ");
        add(TYPE_CAR, "Mini", "Cooper", "Cooper S", "Clubman");
        add(TYPE_CAR, "Tesla", "Model 3", "Model S");
        add(TYPE_CAR, "BYD", "Dolphin", "Seal", "Han");
        add(TYPE_CAR, "MG", "MG4", "MG5");

        // SUV / Crossover
        add(TYPE_SUV, "Renault", "Captur", "Kadjar", "Austral", "Koleos", "Arkana", "Rafale");
        add(TYPE_SUV, "Fiat", "Egea Cross", "500X", "600", "Freemont", "Sedici");
        add(TYPE_SUV, "Volkswagen", "T-Cross", "Taigo", "T-Roc", "Tiguan", "Touareg", "ID.4", "ID.5");
        add(TYPE_SUV, "Ford", "Puma", "Kuga", "EcoSport", "Edge", "Explorer", "Mustang Mach-E");
        add(TYPE_SUV, "Toyota", "C-HR", "Corolla Cross", "RAV4", "Yaris Cross", "Land Cruiser", "Highlander", "bZ4X");
        add(TYPE_SUV, "Hyundai", "Bayon", "Kona", "Tucson", "Santa Fe", "ix35", "Ioniq 5", "Ioniq 6");
        add(TYPE_SUV, "Peugeot", "2008", "3008", "5008");
        add(TYPE_SUV, "Citroën", "C3 Aircross", "C4 Cactus", "C4 X", "C5 Aircross");
        add(TYPE_SUV, "Opel", "Mokka", "Mokka X", "Crossland", "Grandland", "Antara", "Frontera");
        add(TYPE_SUV, "Honda", "HR-V", "CR-V", "ZR-V", "e:Ny1");
        add(TYPE_SUV, "Dacia", "Duster", "Jogger", "Bigster");
        add(TYPE_SUV, "Skoda", "Kamiq", "Karoq", "Kodiaq", "Enyaq", "Yeti");
        add(TYPE_SUV, "SEAT", "Arona", "Ateca", "Tarraco");
        add(TYPE_SUV, "Cupra", "Formentor", "Ateca", "Tavascan", "Terramar");
        add(TYPE_SUV, "Nissan", "Juke", "Qashqai", "X-Trail", "Kicks", "Pathfinder", "Murano");
        add(TYPE_SUV, "Kia", "Stonic", "Niro", "Sportage", "Sorento", "EV3", "EV6", "EV9");
        add(TYPE_SUV, "Mercedes-Benz", "GLA", "GLB", "GLC", "GLE", "GLS", "G Serisi", "EQA", "EQB", "EQC");
        add(TYPE_SUV, "BMW", "X1", "X2", "X3", "X4", "X5", "X6", "X7", "iX1", "iX2", "iX", "XM");
        add(TYPE_SUV, "Audi", "Q2", "Q3", "Q4 e-tron", "Q5", "Q7", "Q8", "Q8 e-tron");
        add(TYPE_SUV, "Volvo", "XC40", "XC60", "XC90", "EX30", "EX40", "EC40");
        add(TYPE_SUV, "Jeep", "Avenger", "Renegade", "Compass", "Cherokee", "Grand Cherokee", "Wrangler");
        add(TYPE_SUV, "Land Rover", "Defender", "Discovery", "Discovery Sport", "Freelander");
        add(TYPE_SUV, "Range Rover", "Evoque", "Velar", "Sport", "Range Rover");
        add(TYPE_SUV, "Porsche", "Macan", "Cayenne");
        add(TYPE_SUV, "Mazda", "CX-3", "CX-30", "CX-5", "CX-60");
        add(TYPE_SUV, "Mitsubishi", "ASX", "Eclipse Cross", "Outlander", "Pajero");
        add(TYPE_SUV, "Suzuki", "Vitara", "S-Cross", "Jimny");
        add(TYPE_SUV, "Subaru", "XV", "Crosstrek", "Forester", "Outback", "Solterra");
        add(TYPE_SUV, "Tesla", "Model Y", "Model X");
        add(TYPE_SUV, "Togg", "T10X", "T10F");
        add(TYPE_SUV, "BYD", "Atto 3", "Seal U", "Tang");
        add(TYPE_SUV, "Chery", "Tiggo 4 Pro", "Tiggo 7 Pro", "Tiggo 8 Pro");
        add(TYPE_SUV, "MG", "ZS", "HS", "Marvel R");
        add(TYPE_SUV, "Skywell", "ET5");
        add(TYPE_SUV, "Leapmotor", "C10");

        // Hafif ticari
        add(TYPE_LCV, "Renault", "Kangoo", "Express", "Trafic", "Master");
        add(TYPE_LCV, "Fiat", "Fiorino", "Doblo", "Scudo", "Ducato", "Talento");
        add(TYPE_LCV, "Volkswagen", "Caddy", "Transporter", "Caravelle", "Multivan", "Crafter");
        add(TYPE_LCV, "Ford", "Transit Courier", "Tourneo Courier", "Transit Connect", "Tourneo Connect", "Transit Custom", "Tourneo Custom", "Transit");
        add(TYPE_LCV, "Peugeot", "Bipper", "Partner", "Rifter", "Expert", "Traveller", "Boxer");
        add(TYPE_LCV, "Citroën", "Nemo", "Berlingo", "Jumpy", "SpaceTourer", "Jumper");
        add(TYPE_LCV, "Opel", "Combo", "Vivaro", "Zafira Life", "Movano");
        add(TYPE_LCV, "Mercedes-Benz", "Citan", "Vito", "V Serisi", "Sprinter");
        add(TYPE_LCV, "Toyota", "Proace City", "Proace", "Hiace");
        add(TYPE_LCV, "Hyundai", "H100", "Staria", "H1", "Starex");
        add(TYPE_LCV, "Nissan", "NV200", "Townstar", "Primastar", "Interstar");
        add(TYPE_LCV, "Isuzu", "D-Max", "N-Series");

        // Pick-up
        add(TYPE_PICKUP, "Ford", "Ranger", "F-150");
        add(TYPE_PICKUP, "Toyota", "Hilux");
        add(TYPE_PICKUP, "Volkswagen", "Amarok");
        add(TYPE_PICKUP, "Mitsubishi", "L200");
        add(TYPE_PICKUP, "Nissan", "Navara");
        add(TYPE_PICKUP, "Isuzu", "D-Max");
        add(TYPE_PICKUP, "SsangYong", "Musso Grand");
        add(TYPE_PICKUP, "Mazda", "BT-50");

        // Ticari / ağır vasıta - ilk seed
        add(TYPE_COMMERCIAL, "Ford", "Cargo", "F-Max", "Transit Şasi Kamyonet");
        add(TYPE_COMMERCIAL, "Mercedes-Benz", "Atego", "Actros", "Arocs", "Sprinter Şasi");
        add(TYPE_COMMERCIAL, "MAN", "TGL", "TGM", "TGS", "TGX");
        add(TYPE_COMMERCIAL, "Volvo", "FL", "FE", "FM", "FH");
        add(TYPE_COMMERCIAL, "Scania", "P Serisi", "G Serisi", "R Serisi", "S Serisi");
        add(TYPE_COMMERCIAL, "Iveco", "Daily", "Eurocargo", "S-Way", "T-Way");
        add(TYPE_COMMERCIAL, "Isuzu", "NPR", "NQR", "NLR", "NMR", "F-Series");
        add(TYPE_COMMERCIAL, "BMC", "Pro", "Tuğra");

        // Motosiklet - temel kapsama
        add(TYPE_MOTORCYCLE, "Honda", "Activa", "Dio", "PCX", "Forza", "CB", "CBR", "NC750X", "Africa Twin");
        add(TYPE_MOTORCYCLE, "Yamaha", "NMAX", "XMAX", "MT-07", "MT-09", "R25", "R7", "Tenere 700", "Tracer 9");
        add(TYPE_MOTORCYCLE, "Suzuki", "Address", "Burgman", "V-Strom", "GSX-R", "GSX-S");
        add(TYPE_MOTORCYCLE, "Kawasaki", "Ninja", "Z", "Versys", "Vulcan");
        add(TYPE_MOTORCYCLE, "BMW Motorrad", "G 310", "F 750 GS", "F 900", "R 1250 GS", "R 1300 GS", "S 1000 RR");
        add(TYPE_MOTORCYCLE, "KTM", "Duke", "RC", "Adventure");
        add(TYPE_MOTORCYCLE, "Vespa", "Primavera", "Sprint", "GTS");
    }

    private static void add(String type, String brand, String... models) {
        LinkedHashMap<String, List<String>> byBrand = DATA.get(type);
        if (byBrand == null) {
            byBrand = new LinkedHashMap<>();
            DATA.put(type, byBrand);
        }
        List<String> list = byBrand.get(brand);
        if (list == null) {
            list = new ArrayList<>();
            byBrand.put(brand, list);
        }
        Collections.addAll(list, models);
    }

    public static String[] vehicleTypes() {
        return DATA.keySet().toArray(new String[0]);
    }

    public static String[] brandsForType(String type) {
        LinkedHashMap<String, List<String>> map = DATA.get(type);
        if (map == null) return new String[]{"Diğer / Manuel"};
        ArrayList<String> out = new ArrayList<>(map.keySet());
        out.add("Diğer / Manuel");
        return out.toArray(new String[0]);
    }

    public static String[] modelsFor(String type, String brand) {
        LinkedHashMap<String, List<String>> map = DATA.get(type);
        if (map == null || brand == null || !map.containsKey(brand)) return new String[]{"Listede yok / Manuel"};
        ArrayList<String> out = new ArrayList<>(map.get(brand));
        out.add("Listede yok / Manuel");
        return out.toArray(new String[0]);
    }

    public static String[] bodyTypesFor(String type) {
        if (TYPE_CAR.equals(type)) return new String[]{"Sedan", "Hatchback", "Station Wagon", "Coupe", "Cabrio", "MPV", "Diğer"};
        if (TYPE_SUV.equals(type)) return new String[]{"SUV", "Crossover", "Arazi", "Diğer"};
        if (TYPE_LCV.equals(type)) return new String[]{"Panelvan", "Camlı Van", "Minivan", "Combi", "Şasi Kabin", "Diğer"};
        if (TYPE_PICKUP.equals(type)) return new String[]{"Çift Kabin", "Tek Kabin", "Diğer"};
        if (TYPE_COMMERCIAL.equals(type)) return new String[]{"Kamyon", "Kamyonet", "Çekici", "Otobüs", "Minibüs", "Şasi Kabin", "Diğer"};
        return new String[]{"Scooter", "Naked", "Sport", "Touring", "Adventure", "Cruiser", "Enduro", "Diğer"};
    }

    public static String[] fuelTypes() {
        return new String[]{"Benzin", "Dizel", "LPG", "Benzin + LPG", "Elektrik", "Hibrit / Benzin", "Hibrit / Dizel", "Plug-in hibrit", "CNG", "Diğer"};
    }

    public static String[] transmissions() {
        return new String[]{"Manuel", "Otomatik", "Yarı otomatik", "CVT / e-CVT", "DCT / EDC / DSG", "Tek oranlı elektrikli", "Diğer"};
    }

    public static String[] years() {
        ArrayList<String> years = new ArrayList<>();
        for (int y = 2026; y >= 1950; y--) years.add(String.valueOf(y));
        return years.toArray(new String[0]);
    }

    public static int modelCount() {
        int count = 0;
        for (Map<String, List<String>> byBrand : DATA.values()) {
            for (List<String> models : byBrand.values()) count += models.size();
        }
        return count;
    }

    public static int brandCount() {
        Set<String> brands = new LinkedHashSet<>();
        for (Map<String, List<String>> byBrand : DATA.values()) brands.addAll(byBrand.keySet());
        return brands.size();
    }
}
