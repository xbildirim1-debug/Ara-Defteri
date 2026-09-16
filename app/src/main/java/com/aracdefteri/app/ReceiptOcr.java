package com.aracdefteri.app;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.android.gms.tasks.Tasks;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded, background OCR with position-aware fields for both images and PDF pages. */
public final class ReceiptOcr {
 private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 public interface Callback {void onResult(RecordParser.Parsed result);void onError(Exception error);}
 public static void process(Context context,Uri uri,Callback callback){
  Context app=context.getApplicationContext();
  WORK.execute(()->{TextRecognizer reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
   try{
    String mime=app.getContentResolver().getType(uri);RecordParser.Parsed result;
    if((mime!=null&&mime.contains("pdf"))||uri.toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))result=pdf(app,uri,reader);
    else {Bitmap image=load(app,uri);try{result=analyze(image,reader);}finally{image.recycle();}}
    MAIN.post(()->callback.onResult(result));
   }catch(Exception e){MAIN.post(()->callback.onError(e));}finally{reader.close();}
  });
 }
 private static Bitmap load(Context context,Uri uri)throws IOException{
  BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
  try(InputStream in=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,o);}
  if(o.outWidth<=0||o.outHeight<=0)throw new IOException("Fotoğraf açılamadı");
  o.inJustDecodeBounds=false;o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>2600)o.inSampleSize*=2;
  Bitmap b;try(InputStream in=context.getContentResolver().openInputStream(uri)){b=BitmapFactory.decodeStream(in,null,o);}
  if(b==null)throw new IOException("Fotoğraf açılamadı");
  int orientation=ExifInterface.ORIENTATION_NORMAL;
  try(InputStream in=context.getContentResolver().openInputStream(uri)){orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(Exception ignored){}
  Matrix m=new Matrix();
  switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;default:break;}
  if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(rotated!=b)b.recycle();b=rotated;}
  return b;
 }
 private static DocumentLayout read(Bitmap bitmap,TextRecognizer reader)throws Exception{
  Text text=Tasks.await(reader.process(InputImage.fromBitmap(bitmap,0)),25,TimeUnit.SECONDS);
  DocumentLayout layout=new DocumentLayout();
  for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines())for(Text.Element e:line.getElements()){
   Rect box=e.getBoundingBox();if(box!=null)layout.add(e.getText(),box.left,box.top,box.right,box.bottom);
  }
  return layout;
 }
 private static RecordParser.Parsed analyze(Bitmap bitmap,TextRecognizer reader)throws Exception{
  DocumentLayout layout=read(bitmap,reader);String raw=layout.text();
  Bitmap enhanced=enhance(bitmap,false);DocumentLayout second;
  try{second=read(enhanced,reader);}finally{enhanced.recycle();}
  // Keep layouts separate: concatenating multiple OCR passes makes duplicated numbers and table headers.
  if(raw.trim().isEmpty()){layout=second;raw=layout.text();}
  if(raw.trim().isEmpty())throw new IOException("Okunabilir yazı bulunamadı. Daha yakın ve net fotoğraf çekebilirsin.");
  RecordParser.Parsed p=RecordParser.fromText(raw);VisualDocumentAnalyzer.applyImage(bitmap,raw,p);layout.apply(p);
  RecordParser.Parsed alt=RecordParser.fromText(second.text());VisualDocumentAnalyzer.sanitizeTextOcr(second.text(),alt);second.apply(alt);
  if(p.type.isEmpty()&&p.documentKind.isEmpty()&&!alt.type.isEmpty())p=alt;
  else if(p.documentKind.equals(alt.documentKind))mergeMissing(p,alt);
  if("ODOMETER".equals(p.documentKind)&&p.km<=0){
   List<DocumentLayout.Word> labels=layout.odometerLabels();if(labels.isEmpty())labels=second.odometerLabels();
   for(DocumentLayout.Word label:labels){
    int x=Math.max(0,(int)label.right),y=Math.max(0,(int)(label.top-label.height()*.6f));
    int w=Math.min(bitmap.getWidth()-x,(int)(label.height()*18)),h=Math.min(bitmap.getHeight()-y,(int)(label.height()*2.7));if(w<=0||h<=0)continue;
    Bitmap crop=Bitmap.createBitmap(bitmap,x,y,w,h),large=Bitmap.createScaledBitmap(crop,w*3,h*3,true);if(crop!=large)crop.recycle();
    try{DocumentLayout focused=read(large,reader);int value=isolatedDigits(focused.text());if(value>0){p.km=value;break;}
     Bitmap inverted=enhance(large,true);try{value=isolatedDigits(read(inverted,reader).text());if(value>0){p.km=value;break;}}finally{inverted.recycle();}
    }finally{large.recycle();}
   }
   if(p.km<=0)p.warnings.add("ODO değeri okunamadı. Hız kadranı rakamları kullanılmadı.");
  }
  if(OilCardParser.isCard(raw)&&p.maintenanceParts.isEmpty()){
   float left=bitmap.getWidth(),top=bitmap.getHeight(),right=0,bottom=0;
   for(DocumentLayout.Word word:layout.words){left=Math.min(left,word.left);top=Math.min(top,word.top);right=Math.max(right,word.right);bottom=Math.max(bottom,word.bottom);}
   int x=Math.max(0,(int)left-25),y=Math.max(0,(int)top-25);
   int w=Math.min(bitmap.getWidth()-x,(int)(right-left)+50),h=Math.min(bitmap.getHeight()-y,(int)(bottom-top)+50);
   if(w>0&&h>0&&w<1400){Bitmap crop=Bitmap.createBitmap(bitmap,x,y,w,h);float scale=Math.min(3f,2600f/Math.max(w,h));Bitmap large=Bitmap.createScaledBitmap(crop,Math.round(w*scale),Math.round(h*scale),true);if(large!=crop)crop.recycle();
    try{String focused=read(large,reader).text();RecordParser.Parsed candidate=RecordParser.fromText(focused);if(OilCardParser.isCard(focused)&&!candidate.maintenanceParts.isEmpty()){p=candidate;}}finally{large.recycle();}
   }
  }
  return p;
 }
 private static int isolatedDigits(String raw){String s=raw.trim();if(s.contains("\n"))return 0;return DocumentLayout.wholeKm(s);}
 private static Bitmap enhance(Bitmap b,boolean invert){
  Bitmap out=Bitmap.createBitmap(b.getWidth(),b.getHeight(),Bitmap.Config.ARGB_8888);Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
  ColorMatrix gray=new ColorMatrix();gray.setSaturation(0);float a=invert?-1.8f:1.5f,v=invert?300:-55;
  gray.postConcat(new ColorMatrix(new float[]{a,0,0,0,v,0,a,0,0,v,0,0,a,0,v,0,0,0,1,0}));paint.setColorFilter(new ColorMatrixColorFilter(gray));new Canvas(out).drawBitmap(b,0,0,paint);return out;
 }
 private static void mergeMissing(RecordParser.Parsed p,RecordParser.Parsed q){
  if(p.vendor.isEmpty())p.vendor=q.vendor;if(p.date.isEmpty())p.date=q.date;
  if(p.policyStartDate.isEmpty())p.policyStartDate=q.policyStartDate;
  if(p.policyEndDate.isEmpty())p.policyEndDate=q.policyEndDate;
  if(p.nextDate.isEmpty())p.nextDate=q.nextDate;if(p.policyNo.isEmpty())p.policyNo=q.policyNo;
  if(p.detailSummary.isEmpty())p.detailSummary=q.detailSummary;
  if(p.km==0)p.km=q.km;else if(q.km>0&&p.km!=q.km){p.km=0;p.warnings.add("Kilometre okumaları uyuşmadı; elle kontrol et.");}
  if(p.quantity==0)p.quantity=q.quantity;if(p.amount==0)p.amount=q.amount;if(p.unitPrice==0)p.unitPrice=q.unitPrice;
  if("Sigorta/Kasko".equals(p.type))p.detailSummary=VisualDocumentAnalyzer.join(p.vendor,p.insuranceSubtype,p.policyNo.isEmpty()?"":"Poliçe: "+p.policyNo,p.policyStartDate.isEmpty()?"":"Başlangıç: "+p.policyStartDate,p.policyEndDate.isEmpty()?"":"Bitiş: "+p.policyEndDate);
 }
 private static RecordParser.Parsed pdf(Context context,Uri uri,TextRecognizer reader)throws Exception{
  try(ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(uri,"r");PdfRenderer pdf=new PdfRenderer(fd)){
   if(pdf.getPageCount()==0)throw new IOException("PDF boş");RecordParser.Parsed combined=null;
   int count=Math.min(pdf.getPageCount(),20);
   for(int i=0;i<count;i++){
    Bitmap image;try(PdfRenderer.Page page=pdf.openPage(i)){float scale=Math.min(2200f/page.getWidth(),2600f/page.getHeight());image=Bitmap.createBitmap(Math.max(1,Math.round(page.getWidth()*scale)),Math.max(1,Math.round(page.getHeight()*scale)),Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);page.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);}
    try{RecordParser.Parsed p=analyze(image,reader);if(combined==null)combined=p;else if(combined.documentKind.equals(p.documentKind)){mergeMissing(combined,p);if(!p.detailSummary.isEmpty()&&!combined.detailSummary.contains(p.detailSummary))combined.detailSummary+="\n"+p.detailSummary;}else combined.warnings.add("PDF farklı belge türleri içeriyor. Her belgeyi ayrı okut.");}finally{image.recycle();}
   }
   if(pdf.getPageCount()>20)combined.warnings.add("Yalnız ilk 20 sayfa okundu.");return combined;
  }
 }
}
