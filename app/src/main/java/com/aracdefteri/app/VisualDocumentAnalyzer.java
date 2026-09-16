package com.aracdefteri.app;
import android.graphics.Bitmap;
import java.util.Map;
import java.util.regex.*;
/** Conservative OCR boundary: numerical guesses and legends never become confirmed values. */
public final class VisualDocumentAnalyzer {
 private VisualDocumentAnalyzer(){}
 public static void sanitizeTextOcr(String raw,RecordParser.Parsed p){
  if(p==null)return;String n=DocumentLayout.norm(raw);
  p.km=explicitKm(n);
  if(OilCardParser.isCard(raw)){OilCardParser.apply(raw,p);return;}
  if("INSURANCE".equals(p.documentKind)||"Sigorta/Kasko".equals(p.type)){
   p.date=p.policyStartDate;p.nextDate=p.policyEndDate;
   p.detailSummary=join(p.vendor,p.insuranceSubtype,p.policyNo.isEmpty()?"":"Poliçe: "+p.policyNo,p.policyStartDate.isEmpty()?"":"Başlangıç: "+p.policyStartDate,p.policyEndDate.isEmpty()?"":"Bitiş: "+p.policyEndDate);
   if(p.vendor.isEmpty())p.warnings.add("Sigorta şirketi okunamadı.");
   if(p.policyEndDate.isEmpty())p.warnings.add("Poliçe bitiş tarihi okunamadı.");
  }
  if("EXPERTISE".equals(p.documentKind)||"Ekspertiz".equals(p.type)){
   p.vendor="";
   if(!DocumentLayout.norm(p.detailSummary).matches(".*(?:kaput|kapi|camurluk|tavan|bagaj|tampon|sasi|podye|direk|motor|fren|obd).*"))p.detailSummary="";
   if(p.detailSummary.isEmpty())p.warnings.add("Parça durumları henüz okunamadı; renk açıklamaları bulgu sayılmadı.");
  }
  if(n.contains("odo")||n.contains("km/h")||n.contains("r/min")){
   if(p.type.isEmpty()||"ODOMETER".equals(p.documentKind)){p.documentKind="ODOMETER";p.type="";p.km=0;}
  }
 }
 public static void applyImage(Bitmap bitmap,String raw,RecordParser.Parsed p){
  sanitizeTextOcr(raw,p);if(bitmap==null||p==null)return;
  if("Ekspertiz".equals(p.type)||"EXPERTISE".equals(p.documentKind)){
   int width=Math.min(800,bitmap.getWidth()),height=Math.round(bitmap.getHeight()*(width/(float)bitmap.getWidth()));
   Bitmap small=Bitmap.createScaledBitmap(bitmap,width,height,true);int[] pixels=new int[width*height];small.getPixels(pixels,0,width,0,0,width,height);if(small!=bitmap)small.recycle();
   Map<String,String> panels=BodyDiagramReader.read(width,height,pixels);
   if(!panels.isEmpty()){
    StringBuilder out=new StringBuilder();for(Map.Entry<String,String> e:panels.entrySet()){if(out.length()>0)out.append('\n');out.append(e.getKey()).append(": ").append(e.getValue());}
    p.detailSummary=out.toString();p.warnings.removeIf(s->s.startsWith("Parça durumları"));p.warnings.add("Yalnız çözümlenen parçalar listelendi. Diğer parçalar hakkında sonuç çıkarılmadı.");
   }
  }
 }
 static int explicitKm(String raw){
  Matcher m=Pattern.compile("(?:\\bodo\\b|odometre|odometer|kilometre|toplam\\s+km|\\bkm(?!\\s*/h)\\b)\\s*[:=-]?\\s*([0-9]{1,3}(?:[., ][0-9]{3})+|[0-9]{3,7})(?![0-9])").matcher(DocumentLayout.norm(raw));
  return m.find()?DocumentLayout.wholeKm(m.group(1)):0;
 }
 static String join(String...xs){StringBuilder b=new StringBuilder();for(String s:xs)if(s!=null&&!s.isEmpty()){if(b.length()>0)b.append(" • ");b.append(s);}return b.toString();}
}
