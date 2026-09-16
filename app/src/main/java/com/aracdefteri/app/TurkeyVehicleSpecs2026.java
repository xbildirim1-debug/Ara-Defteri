package com.aracdefteri.app;
import java.util.*;
/** Current Turkish offerings verified 2026-09-16. Source links: docs/catalog-sources.md.
 * Only the 2026 snapshot is covered; never extrapolate it into older model years. */
final class TurkeyVehicleSpecs2026 {
 private static final Map<String,List<TurkeyVehicleSpecs.Spec>> DATA=new LinkedHashMap<>();
 static {
  add("Volkswagen","T-Cross","1.0 TSI 116 PS Man. • Life","Life","SUV","Benzin","Manuel","1.0 TSI","116 PS");
  add("Volkswagen","T-Cross","1.0 TSI 116 PS DSG • Life","Life","SUV","Benzin","DCT / EDC / DSG","1.0 TSI","116 PS");
  add("Volkswagen","T-Cross","1.0 TSI 116 PS DSG • Style","Style","SUV","Benzin","DCT / EDC / DSG","1.0 TSI","116 PS");
  add("Volkswagen","T-Cross","1.0 TSI 116 PS DSG • R-Line","R-Line","SUV","Benzin","DCT / EDC / DSG","1.0 TSI","116 PS");
  add("Volkswagen","Taigo","1.0 TSI 95 PS Man. • Life","Life","SUV","Benzin","Manuel","1.0 TSI","95 PS");
  add("Volkswagen","Taigo","1.0 TSI 116 PS DSG • Life","Life","SUV","Benzin","DCT / EDC / DSG","1.0 TSI","116 PS");
  add("Volkswagen","Taigo","1.0 TSI 116 PS DSG • Style","Style","SUV","Benzin","DCT / EDC / DSG","1.0 TSI","116 PS");
  add("Volkswagen","Taigo","1.0 TSI 116 PS DSG • R-Line","R-Line","SUV","Benzin","DCT / EDC / DSG","1.0 TSI","116 PS");
  add("Volkswagen","Taigo","1.5 TSI 150 PS DSG • Style","Style","SUV","Benzin","DCT / EDC / DSG","1.5 TSI","150 PS");
  add("Volkswagen","Taigo","1.5 TSI 150 PS DSG • R-Line","R-Line","SUV","Benzin","DCT / EDC / DSG","1.5 TSI","150 PS");
  add("Volkswagen","Golf","1.5 TSI 116 PS Man. • Impression","Impression","Hatchback","Benzin","Manuel","1.5 TSI","116 PS");
  add("Volkswagen","Golf","1.5 eTSI 116 PS DSG • Life","Life","Hatchback","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI","116 PS");
  add("Volkswagen","Golf","1.5 eTSI 116 PS DSG • Style","Style","Hatchback","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI","116 PS");
  add("Volkswagen","Golf","1.5 eTSI 150 PS DSG • Style","Style","Hatchback","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI","150 PS");
  add("Volkswagen","Golf","1.5 eTSI 150 PS DSG • R-Line","R-Line","Hatchback","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI","150 PS");
  add("Volkswagen","Golf","2.0 TSI 265 PS DSG • GTI","GTI","Hatchback","Benzin","DCT / EDC / DSG","2.0 TSI","265 PS");
  add("Volkswagen","Golf","2.0 TSI 333 PS DSG • R","R","Hatchback","Benzin","DCT / EDC / DSG","2.0 TSI","333 PS");
  add("Volkswagen","T-Roc","1.5 eTSI 150 PS DSG • Life","Life","SUV","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI","150 PS");
  add("Volkswagen","T-Roc","1.5 eTSI 150 PS DSG • Style","Style","SUV","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI","150 PS");
  add("Volkswagen","T-Roc","1.5 eTSI 150 PS DSG • R-Line","R-Line","SUV","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI","150 PS");
  add("Volkswagen","Tiguan","1.5 eTSI 150 PS DSG • Life","Life","SUV","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI","150 PS");
  add("Volkswagen","Tiguan","1.5 eTSI 150 PS DSG • Elegance","Elegance","SUV","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI","150 PS");
  add("Volkswagen","Tiguan","1.5 eTSI 150 PS DSG • R-Line","R-Line","SUV","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI","150 PS");
  add("Volkswagen","Tiguan","2.0 TDI 193 PS DSG • Elegance","Elegance","SUV","Dizel","DCT / EDC / DSG","2.0 TDI","193 PS");
  add("Volkswagen","Tiguan","2.0 TDI 193 PS DSG • R-Line","R-Line","SUV","Dizel","DCT / EDC / DSG","2.0 TDI","193 PS");
  add("Volkswagen","Tayron","1.5 eTSI 150 PS DSG • Life","Life","SUV","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI","150 PS");
  add("Volkswagen","Tayron","1.5 eTSI 150 PS DSG • Elegance","Elegance","SUV","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI","150 PS");
  add("Volkswagen","Tayron","1.5 eTSI 150 PS DSG • R-Line","R-Line","SUV","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI","150 PS");
  add("Volkswagen","ID.4","125 kW 170 PS  • Pure","Pure","SUV","Elektrik","Tek oranlı elektrikli","125 kW","170 PS");
  add("Volkswagen","ID.7","210 kW 286 PS  • Pro S","Pro S","Sedan","Elektrik","Tek oranlı elektrikli","210 kW","286 PS");
  add("Volkswagen","Passat","1.5 eTSI ACT 150 PS DSG • Impression","Impression","Station Wagon","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI ACT","150 PS");
  add("Volkswagen","Passat","1.5 eTSI ACT 150 PS DSG • Business","Business","Station Wagon","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI ACT","150 PS");
  add("Volkswagen","Passat","1.5 eTSI ACT 150 PS DSG • Elegance","Elegance","Station Wagon","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI ACT","150 PS");
  add("Volkswagen","Passat","1.5 eTSI ACT 150 PS DSG • R-Line","R-Line","Station Wagon","Hibrit / Benzin","DCT / EDC / DSG","1.5 eTSI ACT","150 PS");
  add("Volkswagen","Passat","2.0 TDI SCR 193 PS DSG • Elegance","Elegance","Station Wagon","Dizel","DCT / EDC / DSG","2.0 TDI SCR","193 PS");
  add("Volkswagen","Passat","2.0 TDI SCR 193 PS DSG • R-Line","R-Line","Station Wagon","Dizel","DCT / EDC / DSG","2.0 TDI SCR","193 PS");
  add("Volkswagen","Passat","2.0 TSI 265 PS DSG • R-Line","R-Line","Station Wagon","Benzin","DCT / EDC / DSG","2.0 TSI","265 PS");
  add("Volkswagen","Touareg","3.0 V6 TDI SCR 286 PS Tiptronic • Elegance","Elegance","SUV","Dizel","Otomatik","3.0 V6 TDI SCR","286 PS");
  add("Hyundai","i20","1.0 T-GDI 90 PS 6MT • Jump","Jump","Hatchback","Benzin","Manuel","1.0 T-GDI","90 PS");
  add("Hyundai","i20","1.0 T-GDI 90 PS 7DCT • Jump","Jump","Hatchback","Benzin","DCT / EDC / DSG","1.0 T-GDI","90 PS");
  add("Hyundai","i20","1.0 T-GDI 90 PS 7DCT • Style","Style","Hatchback","Benzin","DCT / EDC / DSG","1.0 T-GDI","90 PS");
  add("Hyundai","i20","1.0 T-GDI 90 PS 7DCT • Elite","Elite","Hatchback","Benzin","DCT / EDC / DSG","1.0 T-GDI","90 PS");
 }
 private static void add(String brand,String model,String variant,String trim,String body,String fuel,String transmission,String engine,String power){
  String key=brand+"|"+model;
  if(!DATA.containsKey(key))DATA.put(key,new ArrayList<>());
  DATA.get(key).add(new TurkeyVehicleSpecs.Spec(2026,2026,variant,"",trim,body,fuel,transmission,engine,power,""));
 }
 static List<TurkeyVehicleSpecs.Spec> get(String brand,String model,int year){
  if(year!=2026)return null;
  for(String key:DATA.keySet())if(key.equalsIgnoreCase(brand+"|"+model))return new ArrayList<>(DATA.get(key));
  return null;
 }
}
