package com.aracdefteri.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Additional Türkiye-focused technical variants used by the development catalog. */
public final class TurkeyVehicleSpecsExtra {
    private TurkeyVehicleSpecsExtra() {}

    public static List<TurkeyVehicleSpecs.Spec> specsFor(String brand, String model, int year) {
        ArrayList<TurkeyVehicleSpecs.Spec> out = new ArrayList<>();
        String k = norm(brand) + "|" + norm(model);

        // Renault / Dacia
        add(out,k,"renault|captur",2021,2026,"1.3 TCe 140 EDC • Icon","II","Icon","SUV","Benzin","DCT / EDC / DSG","1.3 TCe","140 hp","Önden çekiş",year);
        add(out,k,"renault|captur",2021,2026,"E-Tech full hybrid 145 • Techno","II","Techno","SUV","Hibrit / Benzin","Otomatik","E-Tech full hybrid","145 hp","Önden çekiş",year);
        add(out,k,"renault|megane sedan",2020,2024,"1.5 Blue dCi 115 EDC • Touch","IV","Touch","Sedan","Dizel","DCT / EDC / DSG","1.5 Blue dCi","115 hp","Önden çekiş",year);
        add(out,k,"renault|kangoo",2021,2026,"1.5 Blue dCi 95 • Touch","III","Touch","Combi","Dizel","Manuel","1.5 Blue dCi","95 hp","Önden çekiş",year);
        add(out,k,"dacia|duster",2021,2024,"1.3 TCe 150 EDC • Prestige","II makyajlı","Prestige","SUV","Benzin","DCT / EDC / DSG","1.3 TCe","150 hp","Önden çekiş",year);
        add(out,k,"dacia|duster",2024,2026,"Hybrid 140 • Journey","III","Journey","SUV","Hibrit / Benzin","Otomatik","1.6 Hybrid","140 hp","Önden çekiş",year);
        add(out,k,"dacia|sandero",2021,2026,"1.0 TCe 90 CVT • Stepway","III","Stepway","Hatchback","Benzin","CVT / e-CVT","1.0 TCe","90 hp","Önden çekiş",year);
        add(out,k,"dacia|jogger",2022,2026,"Hybrid 140 • Extreme","I","Extreme","MPV","Hibrit / Benzin","Otomatik","1.6 Hybrid","140 hp","Önden çekiş",year);

        // Fiat
        add(out,k,"fiat|egea sedan",2020,2026,"1.3 Multijet 95 HP • Urban","356","Urban","Sedan","Dizel","Manuel","1.3 Multijet","95 hp","Önden çekiş",year);
        add(out,k,"fiat|egea hatchback",2020,2024,"1.4 Fire 95 HP • Urban","356","Urban","Hatchback","Benzin","Manuel","1.4 Fire","95 hp","Önden çekiş",year);
        add(out,k,"fiat|500",2021,2026,"500e 42 kWh","332","Icon","Hatchback","Elektrik","Tek oranlı elektrikli","Elektrik motoru","118 hp","Önden çekiş",year);
        add(out,k,"fiat|ducato",2021,2026,"2.2 Multijet 140 • Panelvan","X290","Panelvan","Panelvan","Dizel","Manuel","2.2 Multijet","140 hp","Önden çekiş",year);

        // Volkswagen / Skoda / SEAT
        add(out,k,"volkswagen|passat",2020,2024,"1.5 TSI 150 PS DSG • Business","B8","Business","Sedan","Benzin","DCT / EDC / DSG","1.5 TSI","150 hp","Önden çekiş",year);
        add(out,k,"volkswagen|tiguan",2021,2026,"1.5 TSI 150 PS DSG • Elegance","II/III","Elegance","SUV","Benzin","DCT / EDC / DSG","1.5 TSI","150 hp","Önden çekiş",year);
        add(out,k,"volkswagen|caddy",2021,2026,"2.0 TDI 122 PS DSG • Life","V","Life","Combi","Dizel","DCT / EDC / DSG","2.0 TDI","122 hp","Önden çekiş",year);
        add(out,k,"volkswagen|transporter",2020,2024,"2.0 TDI 150 PS DSG • Camlı Van","T6.1","Camlı Van","Camlı Van","Dizel","DCT / EDC / DSG","2.0 TDI","150 hp","Önden çekiş",year);
        add(out,k,"skoda|superb",2020,2024,"1.5 TSI 150 PS DSG • Prestige","III","Prestige","Sedan","Benzin","DCT / EDC / DSG","1.5 TSI","150 hp","Önden çekiş",year);
        add(out,k,"skoda|karoq",2021,2026,"1.5 TSI 150 PS DSG • Prestige","I makyajlı","Prestige","SUV","Benzin","DCT / EDC / DSG","1.5 TSI","150 hp","Önden çekiş",year);
        add(out,k,"skoda|kodiaq",2021,2026,"1.5 TSI 150 PS DSG • Prestige","I/II","Prestige","SUV","Benzin","DCT / EDC / DSG","1.5 TSI","150 hp","Önden çekiş",year);
        add(out,k,"seat|leon",2021,2026,"1.5 eTSI 150 PS DSG • FR","KL","FR","Hatchback","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI","150 hp","Önden çekiş",year);
        add(out,k,"seat|arona",2021,2026,"1.0 EcoTSI 110 PS DSG • FR","I makyajlı","FR","SUV","Benzin","DCT / EDC / DSG","1.0 EcoTSI","110 hp","Önden çekiş",year);

        // Ford
        add(out,k,"ford|fiesta",2020,2023,"1.0 EcoBoost 100 PS • Titanium","VII","Titanium","Hatchback","Benzin","Manuel","1.0 EcoBoost","100 hp","Önden çekiş",year);
        add(out,k,"ford|kuga",2021,2026,"2.5 Duratec PHEV • ST-Line","III","ST-Line","SUV","Plug-in hibrit","Otomatik","2.5 PHEV","225 hp","Önden çekiş",year);
        add(out,k,"ford|transit courier",2024,2026,"1.5 EcoBlue 100 PS • Trend","II","Trend","Panelvan","Dizel","Manuel","1.5 EcoBlue","100 hp","Önden çekiş",year);
        add(out,k,"ford|tourneo courier",2024,2026,"1.0 EcoBoost 125 PS 7DCT • Titanium","II","Titanium","Combi","Benzin","DCT / EDC / DSG","1.0 EcoBoost","125 hp","Önden çekiş",year);
        add(out,k,"ford|ranger",2023,2026,"2.0 EcoBlue 205 PS 10AT • Wildtrak","T6.2","Wildtrak","Çift Kabin","Dizel","Otomatik","2.0 EcoBlue","205 hp","4X4",year);

        // Toyota
        add(out,k,"toyota|yaris",2021,2026,"1.5 Hybrid e-CVT • Flame","XP210","Flame","Hatchback","Hibrit / Benzin","CVT / e-CVT","1.5 Hybrid","116 hp","Önden çekiş",year);
        add(out,k,"toyota|yaris cross",2022,2026,"1.5 Hybrid e-CVT • Passion X-Pack","XP210","Passion X-Pack","SUV","Hibrit / Benzin","CVT / e-CVT","1.5 Hybrid","116 hp","Önden çekiş",year);
        add(out,k,"toyota|rav4",2021,2026,"2.5 Hybrid e-CVT • Passion X-Sport","XA50","Passion X-Sport","SUV","Hibrit / Benzin","CVT / e-CVT","2.5 Hybrid","222 hp","4X4",year);
        add(out,k,"toyota|proace city",2021,2026,"1.5 D-4D 130 AT8 • Dream","K9","Dream","Combi","Dizel","Otomatik","1.5 D-4D","130 hp","Önden çekiş",year);
        add(out,k,"toyota|hilux",2021,2026,"2.4 D-4D 150 6AT • Invincible","AN120","Invincible","Çift Kabin","Dizel","Otomatik","2.4 D-4D","150 hp","4X4",year);

        // Hyundai / Kia
        add(out,k,"hyundai|tucson",2021,2026,"1.6 T-GDI 180 PS 7DCT • Elite Plus","NX4","Elite Plus","SUV","Benzin","DCT / EDC / DSG","1.6 T-GDI","180 hp","Önden çekiş",year);
        add(out,k,"hyundai|elantra",2021,2026,"1.6 MPI 123 PS CVT • Elite","CN7","Elite","Sedan","Benzin","CVT / e-CVT","1.6 MPI","123 hp","Önden çekiş",year);
        add(out,k,"hyundai|kona",2021,2026,"1.6 T-GDI 198 PS 7DCT • Elite","OS/SX2","Elite","SUV","Benzin","DCT / EDC / DSG","1.6 T-GDI","198 hp","Önden çekiş",year);
        add(out,k,"kia|sportage",2022,2026,"1.6 T-GDI 150 PS DCT • Prestige","NQ5","Prestige","SUV","Benzin","DCT / EDC / DSG","1.6 T-GDI","150 hp","Önden çekiş",year);
        add(out,k,"kia|stonic",2021,2026,"1.0 T-GDI 100 PS DCT • Cool","YB","Cool","SUV","Benzin","DCT / EDC / DSG","1.0 T-GDI","100 hp","Önden çekiş",year);
        add(out,k,"kia|niro",2022,2026,"1.6 GDI Hybrid DCT • Prestige","SG2","Prestige","SUV","Hibrit / Benzin","DCT / EDC / DSG","1.6 Hybrid","141 hp","Önden çekiş",year);

        // Peugeot / Citroën / Opel
        add(out,k,"peugeot|208",2021,2026,"1.2 PureTech 100 EAT8 • Allure","II","Allure","Hatchback","Benzin","Otomatik","1.2 PureTech","100 hp","Önden çekiş",year);
        add(out,k,"peugeot|308",2022,2026,"1.2 PureTech 130 EAT8 • GT","III","GT","Hatchback","Benzin","Otomatik","1.2 PureTech","130 hp","Önden çekiş",year);
        add(out,k,"peugeot|408",2023,2026,"1.2 PureTech 130 EAT8 • GT","P54","GT","Crossover","Benzin","Otomatik","1.2 PureTech","130 hp","Önden çekiş",year);
        add(out,k,"peugeot|5008",2021,2026,"1.5 BlueHDi 130 EAT8 • GT","II/III","GT","SUV","Dizel","Otomatik","1.5 BlueHDi","130 hp","Önden çekiş",year);
        add(out,k,"peugeot|expert",2021,2026,"2.0 BlueHDi 145 • Expert","K0","Expert","Panelvan","Dizel","Manuel","2.0 BlueHDi","145 hp","Önden çekiş",year);
        add(out,k,"citroën|c3",2021,2026,"1.2 PureTech 110 EAT6 • Shine","III","Shine","Hatchback","Benzin","Otomatik","1.2 PureTech","110 hp","Önden çekiş",year);
        add(out,k,"citroën|c4",2021,2026,"1.2 PureTech 130 EAT8 • Shine Bold","III","Shine Bold","Hatchback","Benzin","Otomatik","1.2 PureTech","130 hp","Önden çekiş",year);
        add(out,k,"citroën|c5 aircross",2021,2026,"1.5 BlueHDi 130 EAT8 • Shine Bold","I makyajlı","Shine Bold","SUV","Dizel","Otomatik","1.5 BlueHDi","130 hp","Önden çekiş",year);
        add(out,k,"citroën|berlingo",2021,2026,"1.5 BlueHDi 130 EAT8 • Shine","K9","Shine","Combi","Dizel","Otomatik","1.5 BlueHDi","130 hp","Önden çekiş",year);
        add(out,k,"opel|astra",2022,2026,"1.2 Turbo 130 HP AT8 • GS","L","GS","Hatchback","Benzin","Otomatik","1.2 Turbo","130 hp","Önden çekiş",year);
        add(out,k,"opel|mokka",2021,2026,"1.2 Turbo 130 HP AT8 • GS","B","GS","SUV","Benzin","Otomatik","1.2 Turbo","130 hp","Önden çekiş",year);
        add(out,k,"opel|grandland",2021,2026,"1.2 Turbo 130 HP AT8 • Ultimate","A/B","Ultimate","SUV","Benzin","Otomatik","1.2 Turbo","130 hp","Önden çekiş",year);
        add(out,k,"opel|combo",2021,2026,"1.5 Diesel 130 HP AT8 • Ultimate","E","Ultimate","Combi","Dizel","Otomatik","1.5 Diesel","130 hp","Önden çekiş",year);

        // Honda / Nissan
        add(out,k,"honda|city",2021,2026,"1.5 i-VTEC CVT • Executive","GN","Executive","Sedan","Benzin","CVT / e-CVT","1.5 i-VTEC","121 hp","Önden çekiş",year);
        add(out,k,"honda|hr-v",2022,2026,"1.5 e:HEV • Advance","RV","Advance","SUV","Hibrit / Benzin","CVT / e-CVT","1.5 e:HEV","131 hp","Önden çekiş",year);
        add(out,k,"honda|cr-v",2021,2026,"2.0 e:HEV • Executive+","RW/RS","Executive+","SUV","Hibrit / Benzin","CVT / e-CVT","2.0 e:HEV","184 hp","4X4",year);
        add(out,k,"nissan|qashqai",2021,2026,"1.3 DIG-T 158 X-Tronic • Platinum Premium","J12","Platinum Premium","SUV","Hibrit / Benzin","CVT / e-CVT","1.3 DIG-T","158 hp","Önden çekiş",year);
        add(out,k,"nissan|juke",2021,2026,"1.0 DIG-T 115 DCT • Tekna","F16","Tekna","SUV","Benzin","DCT / EDC / DSG","1.0 DIG-T","115 hp","Önden çekiş",year);
        add(out,k,"nissan|x-trail",2023,2026,"e-POWER 4ORCE • Platinum Premium","T33","Platinum Premium","SUV","Hibrit / Benzin","Otomatik","e-POWER","213 hp","4X4",year);

        // Premium brands
        add(out,k,"mercedes-benz|a serisi",2020,2026,"A 200 AMG 7G-DCT","W177","AMG","Hatchback","Benzin","DCT / EDC / DSG","1.3 Turbo","163 hp","Önden çekiş",year);
        add(out,k,"mercedes-benz|c serisi",2022,2026,"C 200 4MATIC AMG 9G-Tronic","W206","AMG","Sedan","Hibrit / Benzin","Otomatik","1.5 Turbo mild hybrid","204 hp","4X4",year);
        add(out,k,"mercedes-benz|glc",2023,2026,"GLC 220 d 4MATIC AMG","X254","AMG","SUV","Hibrit / Dizel","Otomatik","2.0 Diesel mild hybrid","197 hp","4X4",year);
        add(out,k,"mercedes-benz|vito",2020,2026,"114 CDI Select 9G-Tronic","W447","Select","Minivan","Dizel","Otomatik","2.0 CDI","136 hp","Arkadan çekiş",year);
        add(out,k,"bmw|1 serisi",2020,2026,"118i M Sport DCT","F40/F70","M Sport","Hatchback","Benzin","DCT / EDC / DSG","1.5 TwinPower Turbo","136 hp","Önden çekiş",year);
        add(out,k,"bmw|3 serisi",2020,2026,"320i M Sport","G20","M Sport","Sedan","Benzin","Otomatik","1.6/2.0 TwinPower Turbo","170 hp","Arkadan çekiş",year);
        add(out,k,"bmw|x1",2023,2026,"sDrive18i M Sport DCT","U11","M Sport","SUV","Benzin","DCT / EDC / DSG","1.5 TwinPower Turbo","136 hp","Önden çekiş",year);
        add(out,k,"audi|a3",2021,2026,"35 TFSI S tronic • Advanced","8Y","Advanced","Sedan","Hibrit / Benzin","DCT / EDC / DSG","1.5 TFSI","150 hp","Önden çekiş",year);
        add(out,k,"audi|q3",2020,2026,"35 TFSI S tronic • Advanced","F3","Advanced","SUV","Benzin","DCT / EDC / DSG","1.5 TFSI","150 hp","Önden çekiş",year);
        add(out,k,"volvo|xc40",2021,2026,"B3 Mild Hybrid • Plus","XC40","Plus","SUV","Hibrit / Benzin","Otomatik","2.0 mild hybrid","163 hp","Önden çekiş",year);
        add(out,k,"volvo|xc60",2021,2026,"B5 AWD Mild Hybrid • Plus","XC60 II","Plus","SUV","Hibrit / Benzin","Otomatik","2.0 mild hybrid","250 hp","4X4",year);

        // Electric / new-generation Türkiye models
        add(out,k,"tesla|model 3",2021,2026,"RWD","Highland / güncel","RWD","Sedan","Elektrik","Tek oranlı elektrikli","Elektrik motoru","","Arkadan çekiş",year);
        add(out,k,"byd|dolphin",2024,2026,"Design","Dolphin","Design","Hatchback","Elektrik","Tek oranlı elektrikli","Elektrik motoru","204 hp","Önden çekiş",year);
        add(out,k,"byd|seal",2024,2026,"Excellence AWD","Seal","Excellence","Sedan","Elektrik","Tek oranlı elektrikli","Çift elektrik motoru","530 hp","4X4",year);
        add(out,k,"byd|seal u",2024,2026,"DM-i Design","Seal U","Design","SUV","Plug-in hibrit","Otomatik","1.5 PHEV","","Önden çekiş",year);
        add(out,k,"chery|tiggo 7 pro",2023,2026,"1.6 TGDI DCT • Excellent","Tiggo 7 Pro","Excellent","SUV","Benzin","DCT / EDC / DSG","1.6 TGDI","183 hp","Önden çekiş",year);
        add(out,k,"chery|tiggo 8 pro",2023,2026,"1.6 TGDI DCT • Excellent","Tiggo 8 Pro","Excellent","SUV","Benzin","DCT / EDC / DSG","1.6 TGDI","183 hp","Önden çekiş",year);
        add(out,k,"mg|zs",2021,2026,"1.0 T-GDI AT • Luxury","ZS","Luxury","SUV","Benzin","Otomatik","1.0 T-GDI","111 hp","Önden çekiş",year);
        add(out,k,"mg|hs",2021,2026,"1.5 T-GDI DCT • Luxury","HS","Luxury","SUV","Benzin","DCT / EDC / DSG","1.5 T-GDI","162 hp","Önden çekiş",year);

        return out;
    }

    private static void add(List<TurkeyVehicleSpecs.Spec> out, String current, String target,
                            int from, int to, String variant, String generation, String trim,
                            String body, String fuel, String transmission, String engine,
                            String power, String drivetrain, int year) {
        if (!current.equals(target) || year < from || year > to) return;
        out.add(new TurkeyVehicleSpecs.Spec(from, to, variant, generation, trim, body, fuel,
                transmission, engine, power, drivetrain));
    }

    private static String norm(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(new Locale("tr", "TR"));
    }
}
