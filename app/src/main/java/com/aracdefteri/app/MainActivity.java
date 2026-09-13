package com.aracdefteri.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
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
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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
import java.io.OutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_GALLERY_IMAGE = 1001;
    private static final int PICK_RECORD_ATTACHMENT = 1002;
    private static final String PREFS = "arac_defteri_prefs";

    private Database db;
    private SharedPreferences prefs;
    private LinearLayout pageHost;
    private LinearLayout navBar;
    private int currentPage = 0;
    private long pendingAttachmentRecordId = -1;
    private boolean dark;

    private int bg, surface, surface2, text, muted, accent, accent2, warning, danger, stroke;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        db = new Database(this);
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
            bg=Color.rgb(10,15,18); surface=Color.rgb(19,27,32); surface2=Color.rgb(25,37,43);
            text=Color.rgb(245,248,247); muted=Color.rgb(157,174,169); accent=Color.rgb(15,191,133);
            accent2=Color.rgb(37,211,161); warning=Color.rgb(255,184,76); danger=Color.rgb(255,107,107); stroke=Color.rgb(48,64,70);
        } else {
            bg=Color.rgb(243,247,246); surface=Color.WHITE; surface2=Color.rgb(232,242,238);
            text=Color.rgb(23,35,31); muted=Color.rgb(95,112,105); accent=Color.rgb(10,166,117);
            accent2=Color.rgb(0,130,104); warning=Color.rgb(213,134,22); danger=Color.rgb(214,75,75); stroke=Color.rgb(214,226,221);
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
        navBar.setPadding(dp(8),dp(8),dp(8),dp(10));
        navBar.setBackground(cardDrawable(surface,0,stroke));
        root.addView(navBar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(74)));
        setContentView(root);
        buildBottomNav();
    }

    private void buildBottomNav() {
        navBar.removeAllViews();
        String[] labels={"Özet","Kayıtlar","Gider","Galeri","Araç CV"};
        String[] icons={"⌂","≡","₺","▦","CV"};
        for(int i=0;i<labels.length;i++){
            final int index=i;
            LinearLayout item=new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER); item.setPadding(dp(4),dp(5),dp(4),dp(4));
            if(currentPage==i)item.setBackground(cardDrawable(surface2,dp(14),Color.TRANSPARENT));
            TextView icon=tv(icons[i],currentPage==i?accent:muted,16,true); icon.setGravity(Gravity.CENTER);
            TextView label=tv(labels[i],currentPage==i?text:muted,11,currentPage==i); label.setGravity(Gravity.CENTER);
            item.addView(icon,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(24)));
            item.addView(label,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(20)));
            item.setOnClickListener(v->renderPage(index));
            navBar.addView(item,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f));
        }
    }

    private void renderPage(int page){
        currentPage=page; buildBottomNav(); pageHost.removeAllViews();
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(18),dp(14),dp(18),dp(24));
        scroll.addView(content); pageHost.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        if(page==0)renderHome(content); else if(page==1)renderRecords(content); else if(page==2)renderExpenses(content); else if(page==3)renderGallery(content); else renderVehicleCv(content);
    }

    private void addHeader(LinearLayout content,String title,String subtitle){
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts=new LinearLayout(this); texts.setOrientation(LinearLayout.VERTICAL); texts.addView(tv(title,text,25,true)); texts.addView(tv(subtitle,muted,12,false));
        row.addView(texts,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView theme=tv(dark?"☾":"☀",text,20,true); theme.setGravity(Gravity.CENTER); theme.setBackground(cardDrawable(surface,dp(15),stroke)); theme.setOnClickListener(v->showThemeDialog());
        row.addView(theme,new LinearLayout.LayoutParams(dp(48),dp(48))); content.addView(row); gap(content,16);
    }

    private void renderHome(LinearLayout content){
        addHeader(content,"Araç Defteri","Aracının dijital hafızası"); Vehicle vehicle=db.getVehicle();
        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(20),dp(20),dp(20),dp(18));
        GradientDrawable heroBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,dark?new int[]{Color.rgb(11,89,70),Color.rgb(11,43,41)}:new int[]{Color.rgb(8,169,119),Color.rgb(0,111,100)}); heroBg.setCornerRadius(dp(26)); hero.setBackground(heroBg);
        hero.addView(tv("GARAGE 01  •  DEMO",Color.argb(210,255,255,255),11,true)); gap(hero,10);
        hero.addView(tv(vehicle.year+"  "+vehicle.brand+" "+vehicle.model,Color.WHITE,24,true)); hero.addView(tv(vehicle.plate,Color.argb(220,255,255,255),14,false)); gap(hero,18);
        LinearLayout metrics=new LinearLayout(this); metrics.setOrientation(LinearLayout.HORIZONTAL); metrics.addView(metric("KİLOMETRE",formatInt(vehicle.km)+" km"),new LinearLayout.LayoutParams(0,dp(60),1f)); metrics.addView(metric("DURUM","İyi"),new LinearLayout.LayoutParams(0,dp(60),1f)); hero.addView(metrics); gap(hero,14);
        Button edit=smallButton("Araç bilgilerini düzenle"); edit.setOnClickListener(v->showVehicleDialog()); hero.addView(edit); content.addView(hero); gap(content,18);

        sectionTitle(content,"Hızlı kayıt","En sık kullandığın işlemler"); GridLayout quick=new GridLayout(this); quick.setColumnCount(2);
        addQuick(quick,"+  Bakım","Yağ, genel bakım, değişen parça",()->showRecordDialog("Bakım")); addQuick(quick,"+  Hasar","Fotoğraf ve açıklama ekle",()->showRecordDialog("Hasar")); addQuick(quick,"+  Gider","Araçla ilgili bir ödeme",this::showExpenseDialog); addQuick(quick,"+  Yakıt","Dolum ve kilometre kaydı",()->showRecordDialog("Yakıt")); content.addView(quick); gap(content,20);

        sectionTitle(content,"Yaklaşanlar","Tarih ve kilometre bazlı hatırlatmalar"); List<Record> upcoming=db.getUpcomingRecords(3);
        if(upcoming.isEmpty())content.addView(infoCard("Henüz yaklaşan kayıt yok","Bir bakım veya resmî kayıt eklerken sonraki tarih/km belirleyebilirsin.",accent)); else for(Record r:upcoming)content.addView(upcomingCard(r));
        gap(content,20); sectionTitle(content,"Bu ay","Kaydedilen araç harcamaları");
        LinearLayout stat=card(); stat.setPadding(dp(18),dp(16),dp(18),dp(16)); stat.addView(tv(formatMoney(db.getCurrentMonthExpenseTotal()),text,28,true)); stat.addView(tv("Bu ay kaydedilen toplam gider",muted,12,false)); content.addView(stat); gap(content,20);
        sectionTitle(content,"Son hareketler","Araç zaman çizelgesi"); for(Record r:db.getRecords(4))content.addView(recordCard(r,false));
    }

    private void renderRecords(LinearLayout content){
        addHeader(content,"Kayıtlar","Bakım, hasar, ekspertiz ve resmî işlemler"); Button add=primaryButton("+ Yeni kayıt"); add.setOnClickListener(v->showRecordDialog(null)); content.addView(add); gap(content,16);
        String[] names={"Bakım","Hasar","Ekspertiz","Sigorta","Muayene","Vergi"}; LinearLayout chips=new LinearLayout(this); chips.setOrientation(LinearLayout.HORIZONTAL);
        for(String n:names){TextView chip=tv(n,text,12,true); chip.setGravity(Gravity.CENTER); chip.setPadding(dp(11),dp(7),dp(11),dp(7)); chip.setBackground(cardDrawable(surface2,dp(18),Color.TRANSPARENT)); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(36)); cp.setMargins(0,0,dp(7),0); chips.addView(chip,cp);} 
        android.widget.HorizontalScrollView hsv=new android.widget.HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false); hsv.addView(chips); content.addView(hsv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46))); gap(content,10);
        List<Record> records=db.getRecords(100); if(records.isEmpty())content.addView(infoCard("Kayıt bulunamadı","Bakım, hasar veya ekspertiz kaydı ekleyerek aracın geçmişini oluşturmaya başla.",accent)); else for(Record r:records)content.addView(recordCard(r,true));
    }

    private void renderExpenses(LinearLayout content){
        addHeader(content,"Giderler","Aracın gerçek maliyetini tek yerde gör"); LinearLayout total=card(); total.setPadding(dp(18),dp(18),dp(18),dp(18)); total.addView(tv("TOPLAM KAYITLI GİDER",muted,11,true)); total.addView(tv(formatMoney(db.getExpenseTotal()),text,30,true)); total.addView(tv("Bakım kayıtlarındaki maliyetler + diğer giderler",muted,12,false)); content.addView(total); gap(content,14);
        Button add=primaryButton("+ Gider ekle"); add.setOnClickListener(v->showExpenseDialog()); content.addView(add); gap(content,18); sectionTitle(content,"Gider geçmişi","Otopark, yıkama, otoyol, aksesuar ve diğerleri");
        for(Expense e:db.getExpenses()){LinearLayout c=card(); c.setPadding(dp(16),dp(14),dp(16),dp(14)); LinearLayout line=new LinearLayout(this); line.setOrientation(LinearLayout.HORIZONTAL); line.addView(tv(e.category,text,15,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); line.addView(tv(formatMoney(e.amount),accent,15,true)); c.addView(line); c.addView(tv(e.date+(e.note.isEmpty()?"":"  •  "+e.note),muted,12,false)); content.addView(c); gap(content,8);}
    }

    private void renderGallery(LinearLayout content){
        addHeader(content,"Galeri","Aracının zaman içindeki hikâyesi"); Button add=primaryButton("+ Fotoğraf ekle"); add.setOnClickListener(v->pickGalleryImage()); content.addView(add); gap(content,14); content.addView(infoCard("Galeri ile hasar fotoğrafları ayrı","Bu alan aracının normal fotoğrafları için. Hasar ve ekspertiz belgeleri ilgili kayıtların içinde saklanır.",accent)); gap(content,16);
        List<Photo> photos=db.getPhotos(); if(photos.isEmpty()){LinearLayout empty=card(); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(18),dp(34),dp(18),dp(34)); empty.addView(tv("▦",muted,38,true)); TextView t=tv("Henüz fotoğraf yok",text,17,true); t.setGravity(Gravity.CENTER); TextView s=tv("Yıkama sonrası, gezi veya aracını ilk aldığın gün gibi fotoğrafları ekleyebilirsin.",muted,12,false); s.setGravity(Gravity.CENTER); empty.addView(t); empty.addView(s); content.addView(empty);} else for(Photo p:photos){LinearLayout c=card(); c.setPadding(dp(7),dp(7),dp(7),dp(12)); ImageView image=new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); try{image.setImageURI(Uri.parse(p.uri));}catch(Exception ignored){} c.addView(image,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(220))); TextView cap=tv(p.date+"  •  Araç galerisi",muted,12,false); cap.setPadding(dp(8),dp(8),dp(8),0); c.addView(cap); content.addView(c); gap(content,10);}
    }

    private void renderVehicleCv(LinearLayout content){
        addHeader(content,"Araç CV","Aracın geçmişini düzenli bir PDF'e dönüştür"); Vehicle v=db.getVehicle(); LinearLayout preview=card(); preview.setPadding(dp(20),dp(20),dp(20),dp(20)); preview.addView(tv("ARAÇ GEÇMİŞ RAPORU",accent,11,true)); preview.addView(tv(v.year+" "+v.brand+" "+v.model,text,24,true)); preview.addView(tv(v.plate+"  •  "+formatInt(v.km)+" km",muted,13,false)); gap(preview,16); preview.addView(cvLine("Bakım / onarım kayıtları",String.valueOf(db.countRecordsByType("Bakım")))); preview.addView(cvLine("Hasar kayıtları",String.valueOf(db.countRecordsByType("Hasar")))); preview.addView(cvLine("Ekspertiz kayıtları",String.valueOf(db.countRecordsByType("Ekspertiz")))); preview.addView(cvLine("Toplam kayıt",String.valueOf(db.countAllRecords()))); content.addView(preview); gap(content,16); content.addView(infoCard("Şeffaf geçmiş","PDF, kullanıcının kendi girdiği kayıtları özetler. Resmî ekspertiz veya kilometre doğrulama belgesi değildir.",warning)); gap(content,14); Button pdf=primaryButton("PDF Araç CV oluştur"); pdf.setOnClickListener(vw->createVehiclePdf()); content.addView(pdf);
    }

    private void showThemeDialog(){String[] items={"Sistem temasını kullan","Açık tema","Koyu tema"}; new AlertDialog.Builder(this).setTitle("Görünüm").setItems(items,(d,w)->{prefs.edit().putString("theme_mode",w==0?"system":w==1?"light":"dark").apply(); recreate();}).show();}

    private void showVehicleDialog(){
        Vehicle v=db.getVehicle(); LinearLayout form=formLayout(); EditText brand=field("Marka",v.brand), model=field("Model",v.model), year=numberField("Model yılı",String.valueOf(v.year)), plate=field("Plaka",v.plate), km=numberField("Kilometre",String.valueOf(v.km)); form.addView(brand);form.addView(model);form.addView(year);form.addView(plate);form.addView(km);
        new AlertDialog.Builder(this).setTitle("Araç bilgileri").setView(form).setNegativeButton("Vazgeç",null).setPositiveButton("Kaydet",(d,w)->{db.updateVehicle(brand.getText().toString().trim(),model.getText().toString().trim(),safeInt(year.getText().toString(),v.year),plate.getText().toString().trim(),safeInt(km.getText().toString(),v.km));renderPage(currentPage);}).show();
    }

    private void showRecordDialog(String presetType){
        LinearLayout form=formLayout(); String[] types={"Bakım","Hasar","Ekspertiz","Sigorta/Kasko","Muayene","Vergi","Yakıt","Diğer"}; Spinner type=new Spinner(this); type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types)); if(presetType!=null)for(int i=0;i<types.length;i++)if(types[i].equals(presetType))type.setSelection(i); form.addView(label("Kayıt türü")); form.addView(type,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));
        EditText title=field("Başlık / yapılan işlem",presetType!=null&&presetType.equals("Bakım")?"Yağ bakımı / genel bakım":""), date=field("Tarih (gg.aa.yyyy)",today()), km=numberField("Kilometre",String.valueOf(db.getVehicle().km)), cost=decimalField("Tutar",""), detail=field("Açıklama / değişen parçalar",""), nextDate=field("Sonraki tarih (isteğe bağlı)",""), nextKm=numberField("Sonraki kilometre (isteğe bağlı)",""); detail.setMinLines(2); detail.setGravity(Gravity.TOP); form.addView(title);form.addView(date);form.addView(km);form.addView(cost);form.addView(detail);form.addView(nextDate);form.addView(nextKm);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Yeni araç kaydı").setView(form).setNegativeButton("Vazgeç",null).setPositiveButton("Kaydet",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String chosen=type.getSelectedItem().toString(); String titleText=title.getText().toString().trim(); if(titleText.isEmpty())titleText=chosen; long id=db.addRecord(chosen,titleText,date.getText().toString().trim(),safeInt(km.getText().toString(),0),safeDouble(cost.getText().toString()),detail.getText().toString().trim(),nextDate.getText().toString().trim(),safeInt(nextKm.getText().toString(),0)); if(!nextDate.getText().toString().trim().isEmpty())scheduleDateReminders(id,titleText,nextDate.getText().toString().trim()); Toast.makeText(this,"Kayıt eklendi",Toast.LENGTH_SHORT).show(); dialog.dismiss(); renderPage(currentPage);})); dialog.show();
    }

    private void showExpenseDialog(){
        LinearLayout form=formLayout(); String[] cats={"Otopark","Otoyol / geçiş","Yıkama","Aksesuar","Vergi","Sigorta","Diğer"}; Spinner category=new Spinner(this); category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,cats)); form.addView(label("Kategori")); form.addView(category,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52))); EditText amount=decimalField("Tutar",""), date=field("Tarih",today()), note=field("Not",""); form.addView(amount);form.addView(date);form.addView(note); new AlertDialog.Builder(this).setTitle("Gider ekle").setView(form).setNegativeButton("Vazgeç",null).setPositiveButton("Kaydet",(d,w)->{db.addExpense(category.getSelectedItem().toString(),date.getText().toString().trim(),safeDouble(amount.getText().toString()),note.getText().toString().trim());renderPage(currentPage);}).show();
    }

    private void pickGalleryImage(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*"); startActivityForResult(i,PICK_GALLERY_IMAGE);}
    private void pickRecordAttachment(long id){pendingAttachmentRecordId=id; Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","application/pdf"});startActivityForResult(i,PICK_RECORD_ATTACHMENT);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data); if(resultCode!=RESULT_OK||data==null||data.getData()==null)return; Uri uri=data.getData(); try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){} if(requestCode==PICK_GALLERY_IMAGE){db.addPhoto(uri.toString(),today());renderPage(3);}else if(requestCode==PICK_RECORD_ATTACHMENT&&pendingAttachmentRecordId>0){db.setRecordAttachment(pendingAttachmentRecordId,uri.toString());pendingAttachmentRecordId=-1;Toast.makeText(this,"Belge kayda eklendi",Toast.LENGTH_SHORT).show();renderPage(1);}}

    private void openAttachment(String u){try{Intent i=new Intent(Intent.ACTION_VIEW);i.setData(Uri.parse(u));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception e){Toast.makeText(this,"Belge açılamadı",Toast.LENGTH_SHORT).show();}}

    private void scheduleDateReminders(long id,String title,String dateText){try{Date due=new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).parse(dateText);if(due==null)return;scheduleOneReminder(id,title,due.getTime()-7L*86400000L,7);scheduleOneReminder(id,title,due.getTime()-86400000L,1);}catch(Exception ignored){}}
    private void scheduleOneReminder(long id,String title,long triggerAt,int days){if(triggerAt<=System.currentTimeMillis())return;Intent i=new Intent(this,ReminderReceiver.class);i.putExtra("title",title+" yaklaşıyor");i.putExtra("text",days+" gün kaldı. Araç Defteri kaydını kontrol et.");int request=(int)((id*10+days)%Integer.MAX_VALUE);PendingIntent pi=PendingIntent.getBroadcast(this,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);((AlarmManager)getSystemService(ALARM_SERVICE)).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,triggerAt,pi);}
    private void requestNotificationPermissionIfNeeded(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},301);}

    private void createVehiclePdf(){
        android.graphics.pdf.PdfDocument doc=new android.graphics.pdf.PdfDocument(); Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); Vehicle vehicle=db.getVehicle(); List<Record> records=db.getRecords(30); int pageW=595,pageH=842,pageNo=1,y=60; android.graphics.pdf.PdfDocument.Page page=doc.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW,pageH,pageNo).create()); Canvas canvas=page.getCanvas();
        paint.setColor(Color.rgb(10,166,117));paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));paint.setTextSize(24);canvas.drawText("ARAÇ DEFTERİ • ARAÇ CV",42,y,paint);y+=42;paint.setColor(Color.BLACK);paint.setTextSize(20);canvas.drawText(vehicle.year+" "+vehicle.brand+" "+vehicle.model,42,y,paint);y+=25;paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(11);canvas.drawText(vehicle.plate+"  •  "+formatInt(vehicle.km)+" km",42,y,paint);y+=30;paint.setColor(Color.DKGRAY);canvas.drawText("Bu rapor kullanıcı tarafından girilen kayıtların özetidir; resmî ekspertiz belgesi değildir.",42,y,paint);y+=32;paint.setColor(Color.BLACK);paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));paint.setTextSize(15);canvas.drawText("Araç geçmişi",42,y,paint);y+=22;paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(10);
        for(Record r:records){if(y>770){doc.finishPage(page);pageNo++;page=doc.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW,pageH,pageNo).create());canvas=page.getCanvas();y=55;}paint.setColor(Color.BLACK);paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));canvas.drawText(r.type+" • "+r.title,42,y,paint);y+=14;paint.setTypeface(Typeface.DEFAULT);paint.setColor(Color.DKGRAY);canvas.drawText(r.date+"  |  "+formatInt(r.km)+" km"+(r.cost>0?"  |  "+formatMoney(r.cost):""),42,y,paint);y+=13;if(!r.detail.isEmpty()){String s=r.detail.length()>78?r.detail.substring(0,78)+"…":r.detail;canvas.drawText(s,42,y,paint);y+=13;}y+=7;} doc.finishPage(page);
        try{String fileName="Arac-CV-"+System.currentTimeMillis()+".pdf";Uri uri;if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,fileName);values.put(MediaStore.MediaColumns.MIME_TYPE,"application/pdf");values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/AracDefteri");uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new Exception("Dosya oluşturulamadı");try(OutputStream out=getContentResolver().openOutputStream(uri)){doc.writeTo(out);}}else{File dir=getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);if(dir==null)throw new Exception("Klasör bulunamadı");File file=new File(dir,fileName);try(OutputStream out=new FileOutputStream(file)){doc.writeTo(out);}uri=Uri.fromFile(file);}doc.close();Toast.makeText(this,"Araç CV PDF oluşturuldu",Toast.LENGTH_LONG).show();if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q)openPdf(uri);}catch(Exception e){doc.close();Toast.makeText(this,"PDF oluşturulamadı: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void openPdf(Uri uri){try{Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,"application/pdf");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception ignored){}}

    private LinearLayout metric(String label,String value){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(10),dp(8),dp(10),dp(8));b.addView(tv(label,Color.argb(180,255,255,255),10,true));b.addView(tv(value,Color.WHITE,18,true));return b;}
    private LinearLayout cvLine(String n,String v){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setPadding(0,dp(7),0,dp(7));r.addView(tv(n,text,13,false),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));r.addView(tv(v,accent,14,true));return r;}
    private void addQuick(GridLayout grid,String title,String sub,Runnable action){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(15),dp(15),dp(15),dp(15));box.setBackground(cardDrawable(surface,dp(20),stroke));box.addView(tv(title,text,15,true));box.addView(tv(sub,muted,11,false));box.setOnClickListener(v->action.run());GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.height=dp(92);lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);lp.setMargins(0,0,dp(8),dp(8));grid.addView(box,lp);}
    private LinearLayout upcomingCard(Record r){LinearLayout c=card();c.setPadding(dp(16),dp(14),dp(16),dp(14));LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.addView(tv("●",warning,14,true),new LinearLayout.LayoutParams(dp(24),ViewGroup.LayoutParams.WRAP_CONTENT));LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(tv(r.title,text,15,true));String next=!r.nextDate.isEmpty()?r.nextDate:(r.nextKm>0?formatInt(r.nextKm)+" km":"Hatırlatma");t.addView(tv(r.type+"  •  "+next,muted,12,false));row.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));c.addView(row);return c;}
    private LinearLayout recordCard(Record r,boolean actions){LinearLayout c=card();c.setPadding(dp(16),dp(14),dp(16),dp(14));LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);TextView ty=tv(r.type.toUpperCase(Locale.getDefault()),categoryColor(r.type),10,true);ty.setPadding(dp(8),dp(4),dp(8),dp(4));ty.setBackground(cardDrawable(surface2,dp(12),Color.TRANSPARENT));top.addView(ty);top.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1f));top.addView(tv(r.date,muted,11,false));c.addView(top);gap(c,8);c.addView(tv(r.title,text,16,true));c.addView(tv(formatInt(r.km)+" km"+(r.cost>0?"  •  "+formatMoney(r.cost):""),muted,12,false));if(!r.detail.isEmpty()){TextView d=tv(r.detail,muted,12,false);d.setPadding(0,dp(5),0,0);c.addView(d);}if(!r.nextDate.isEmpty()||r.nextKm>0){String n="Sonraki: "+(!r.nextDate.isEmpty()?r.nextDate:"")+(!r.nextDate.isEmpty()&&r.nextKm>0?" • ":"")+(r.nextKm>0?formatInt(r.nextKm)+" km":"");TextView nt=tv(n,warning,11,true);nt.setPadding(0,dp(7),0,0);c.addView(nt);}if(actions){gap(c,10);Button a=smallButton(r.attachment.isEmpty()?"Belge / fotoğraf ekle":"Belgeyi aç");a.setOnClickListener(v->{if(r.attachment.isEmpty())pickRecordAttachment(r.id);else openAttachment(r.attachment);});c.addView(a,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(42)));}LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,0,0,dp(9));c.setLayoutParams(lp);return c;}
    private int categoryColor(String t){if(t.equals("Hasar"))return danger;if(t.equals("Ekspertiz"))return warning;if(t.equals("Bakım"))return accent;return accent2;}
    private void sectionTitle(LinearLayout p,String title,String sub){p.addView(tv(title,text,18,true));TextView s=tv(sub,muted,11,false);s.setPadding(0,dp(2),0,dp(10));p.addView(s);}
    private LinearLayout infoCard(String titleText,String subtitle,int color){LinearLayout b=card();b.setPadding(dp(16),dp(14),dp(16),dp(14));LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);line.addView(tv("●",color,12,true),new LinearLayout.LayoutParams(dp(24),ViewGroup.LayoutParams.WRAP_CONTENT));LinearLayout texts=new LinearLayout(this);texts.setOrientation(LinearLayout.VERTICAL);texts.addView(tv(titleText,text,14,true));texts.addView(tv(subtitle,muted,11,false));line.addView(texts,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));b.addView(line);return b;}
    private LinearLayout card(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setBackground(cardDrawable(surface,dp(20),stroke));return v;}
    private GradientDrawable cardDrawable(int color,int radius,int strokeColor){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);if(strokeColor!=Color.TRANSPARENT)d.setStroke(dp(1),strokeColor);return d;}
    private TextView tv(String value,int color,int sp,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(sp);if(bold)t.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));t.setLineSpacing(0,1.08f);return t;}
    private TextView label(String value){TextView t=tv(value,muted,11,true);t.setPadding(0,dp(7),0,dp(3));return t;}
    private Button primaryButton(String value){Button b=new Button(this);b.setText(value);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setAllCaps(false);b.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));b.setBackground(cardDrawable(accent,dp(16),Color.TRANSPARENT));b.setMinHeight(dp(52));return b;}
    private Button smallButton(String value){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(dark?Color.WHITE:text);b.setBackground(cardDrawable(dark?Color.argb(35,255,255,255):Color.argb(45,255,255,255),dp(14),dark?Color.argb(55,255,255,255):stroke));return b;}
    private LinearLayout formLayout(){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(18),dp(4),dp(18),dp(6));return f;}
    private EditText field(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(false);e.setTextSize(14);e.setPadding(dp(10),dp(10),dp(10),dp(10));return e;}
    private EditText numberField(String hint,String value){EditText e=field(hint,value);e.setInputType(InputType.TYPE_CLASS_NUMBER);return e;}
    private EditText decimalField(String hint,String value){EditText e=field(hint,value);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);return e;}
    private void gap(LinearLayout p,int h){p.addView(new Space(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private String today(){return new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).format(new Date());}
    private String formatMoney(double v){NumberFormat nf=NumberFormat.getNumberInstance(new Locale("tr","TR"));nf.setMaximumFractionDigits(2);return nf.format(v)+" ₺";}
    private String formatInt(int v){return NumberFormat.getIntegerInstance(new Locale("tr","TR")).format(v);}
    private int safeInt(String s,int f){try{return Integer.parseInt(s.trim());}catch(Exception e){return f;}}
    private double safeDouble(String s){try{return Double.parseDouble(s.trim().replace(',','.'));}catch(Exception e){return 0;}}

    static class Vehicle{int id,year,km;String brand,model,plate;}
    static class Record{long id;String type,title,date,detail,nextDate,attachment;int km,nextKm;double cost;}
    static class Expense{long id;String category,date,note;double amount;}
    static class Photo{long id;String uri,date;}

    static class Database extends SQLiteOpenHelper{
        Database(Context c){super(c,"arac_defteri.db",null,1);}
        @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE vehicle (id INTEGER PRIMARY KEY, brand TEXT, model TEXT, year INTEGER, plate TEXT, km INTEGER)");db.execSQL("CREATE TABLE records (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, title TEXT, date TEXT, km INTEGER, cost REAL, detail TEXT, next_date TEXT, next_km INTEGER, attachment TEXT)");db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT, date TEXT, amount REAL, note TEXT)");db.execSQL("CREATE TABLE photos (id INTEGER PRIMARY KEY AUTOINCREMENT, uri TEXT, date TEXT)");}
        @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
        void seedDemoIfNeeded(){SQLiteDatabase w=getWritableDatabase();Cursor c=w.rawQuery("SELECT COUNT(*) FROM vehicle",null);c.moveToFirst();int n=c.getInt(0);c.close();if(n==0){ContentValues v=new ContentValues();v.put("id",1);v.put("brand","Toyota");v.put("model","Corolla Hybrid");v.put("year",2021);v.put("plate","34 DEM 2026");v.put("km",68420);w.insert("vehicle",null,v);addRecord("Bakım","Yağ bakımı","02.09.2026",67120,4250,"Motor yağı, yağ filtresi ve polen filtresi değişti.","02.09.2027",77120);addRecord("Ekspertiz","Yıllık genel kontrol","17.08.2026",65840,1500,"Kaporta, fren ve alt takım kontrol edildi. Demo kayıt.","",0);addRecord("Hasar","Sağ arka tampon çizik onarımı","21.06.2026",63210,2800,"Lokal boya uygulandı. Demo kayıt.","",0);addRecord("Muayene","Periyodik muayene","10.04.2026",60130,0,"Kusursuz geçti. Demo kayıt.","10.04.2028",0);addExpense("Yıkama","08.09.2026",500,"İç-dış yıkama");addExpense("Otopark","05.09.2026",220,"Şehir merkezi");}}
        Vehicle getVehicle(){Vehicle v=new Vehicle();Cursor c=getReadableDatabase().rawQuery("SELECT id,brand,model,year,plate,km FROM vehicle LIMIT 1",null);if(c.moveToFirst()){v.id=c.getInt(0);v.brand=c.getString(1);v.model=c.getString(2);v.year=c.getInt(3);v.plate=c.getString(4);v.km=c.getInt(5);}c.close();return v;}
        void updateVehicle(String brand,String model,int year,String plate,int km){ContentValues v=new ContentValues();v.put("brand",brand);v.put("model",model);v.put("year",year);v.put("plate",plate);v.put("km",km);getWritableDatabase().update("vehicle",v,"id=1",null);}
        long addRecord(String type,String title,String date,int km,double cost,String detail,String nextDate,int nextKm){ContentValues v=new ContentValues();v.put("type",type);v.put("title",title);v.put("date",date);v.put("km",km);v.put("cost",cost);v.put("detail",detail);v.put("next_date",nextDate);v.put("next_km",nextKm);v.put("attachment","");return getWritableDatabase().insert("records",null,v);}
        void setRecordAttachment(long id,String uri){ContentValues v=new ContentValues();v.put("attachment",uri);getWritableDatabase().update("records",v,"id=?",new String[]{String.valueOf(id)});}
        List<Record> getRecords(int limit){List<Record> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,type,title,date,km,cost,detail,next_date,next_km,attachment FROM records ORDER BY id DESC LIMIT "+limit,null);while(c.moveToNext())out.add(readRecord(c));c.close();return out;}
        List<Record> getUpcomingRecords(int limit){List<Record> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,type,title,date,km,cost,detail,next_date,next_km,attachment FROM records WHERE next_date<>'' OR next_km>0 ORDER BY id DESC LIMIT "+limit,null);while(c.moveToNext())out.add(readRecord(c));c.close();return out;}
        Record readRecord(Cursor c){Record r=new Record();r.id=c.getLong(0);r.type=c.getString(1);r.title=c.getString(2);r.date=c.getString(3);r.km=c.getInt(4);r.cost=c.getDouble(5);r.detail=c.getString(6);r.nextDate=c.getString(7);r.nextKm=c.getInt(8);r.attachment=c.getString(9);if(r.attachment==null)r.attachment="";return r;}
        long addExpense(String category,String date,double amount,String note){ContentValues v=new ContentValues();v.put("category",category);v.put("date",date);v.put("amount",amount);v.put("note",note);return getWritableDatabase().insert("expenses",null,v);}
        List<Expense> getExpenses(){List<Expense> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,category,date,amount,note FROM expenses ORDER BY id DESC",null);while(c.moveToNext()){Expense e=new Expense();e.id=c.getLong(0);e.category=c.getString(1);e.date=c.getString(2);e.amount=c.getDouble(3);e.note=c.getString(4);out.add(e);}c.close();return out;}
        double getCurrentMonthExpenseTotal(){double total=0;String suffix=new SimpleDateFormat("MM.yyyy",Locale.getDefault()).format(new Date());Cursor c=getReadableDatabase().rawQuery("SELECT amount,date FROM expenses",null);while(c.moveToNext())if(c.getString(1).endsWith(suffix))total+=c.getDouble(0);c.close();Cursor r=getReadableDatabase().rawQuery("SELECT cost,date FROM records WHERE cost>0",null);while(r.moveToNext())if(r.getString(1).endsWith(suffix))total+=r.getDouble(0);r.close();return total;}
        double getExpenseTotal(){double total=0;Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM expenses",null);c.moveToFirst();total+=c.getDouble(0);c.close();Cursor r=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(cost),0) FROM records",null);r.moveToFirst();total+=r.getDouble(0);r.close();return total;}
        void addPhoto(String uri,String date){ContentValues v=new ContentValues();v.put("uri",uri);v.put("date",date);getWritableDatabase().insert("photos",null,v);}
        List<Photo> getPhotos(){List<Photo> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,uri,date FROM photos ORDER BY id DESC",null);while(c.moveToNext()){Photo p=new Photo();p.id=c.getLong(0);p.uri=c.getString(1);p.date=c.getString(2);out.add(p);}c.close();return out;}
        int countRecordsByType(String type){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM records WHERE type=?",new String[]{type});c.moveToFirst();int n=c.getInt(0);c.close();return n;}
        int countAllRecords(){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM records",null);c.moveToFirst();int n=c.getInt(0);c.close();return n;}
    }
}
