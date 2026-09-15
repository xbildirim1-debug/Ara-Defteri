package com.aracdefteri.app;

import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;

@RunWith(AndroidJUnit4.class)
public class StorageAndUiTest {
    private Context c;
    @Before public void clean(){
        c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.deleteDatabase("arac_defteri.db");
        for(String name:new String[]{"garage","arac_defteri_prefs","vehicle_1","vehicle_2","vehicle_draft"})c.getSharedPreferences(name,0).edit().clear().commit();
    }
    @Test public void vehicleIsolationPortableBackupAndCorruptionRollback()throws Exception{
        try(AppDatabase db=new AppDatabase(c)){
            assertTrue(db.vehicles().isEmpty());
            int first=db.createVehicle("Toyota","Corolla",2021,68420,"Benzin");
            AppDatabase.Record record=new AppDatabase.Record();record.type="Bakım";record.title="Yağ ve filtre";record.date="15.09.2026";record.km=68420;record.nextDate="16.09.2026";record.metadata="{\"service\":\"Örnek servis\"}";record.id=db.addRecord(record);
            File photo=new File(c.getCacheDir(),"fixture.png");Bitmap bitmap=Bitmap.createBitmap(300,150,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE);
            try(OutputStream out=new FileOutputStream(photo)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
            Uri owned=MediaFiles.copy(c,Uri.fromFile(photo));db.addAttachment(record.id,owned.toString(),"Fatura");photo.delete();
            assertNotNull(c.getContentResolver().openInputStream(owned));
            VehiclePreferences.open(c,first).edit().putString("vehicle_color","Mavi").commit();
            int second=db.createVehicle("Renault","Clio",2020,42000,"Benzin");assertTrue(db.getRecords(50).isEmpty());assertNull(db.getRecord(record.id));
            db.selectVehicle(first);assertEquals(1,db.getRecords(50).size());db.updateKm(69000,"Manuel");assertEquals(1,db.kmHistory().size());
            File archive=new File(c.getCacheDir(),"backup.zip");BackupManager.exportTo(c,db,Uri.fromFile(archive));
            db.deleteRecord(record.id);db.updateKm(99999,"Manuel");
            BackupManager.restore(c,db,Uri.fromFile(archive));assertEquals(69000,db.getVehicle().km);assertEquals(2,db.vehicles().size());assertEquals("Mavi",VehiclePreferences.open(c,first).getString("vehicle_color",""));assertEquals(1,db.attachments(record.id).size());
            try(InputStream in=c.getContentResolver().openInputStream(Uri.parse(db.attachments(record.id).get(0).uri))){assertTrue(in.read()>=0);}
            File broken=new File(c.getCacheDir(),"broken.zip");try(OutputStream out=new FileOutputStream(broken)){out.write(new byte[]{1,2,3});}
            try{BackupManager.restore(c,db,Uri.fromFile(broken));fail("Corrupt archive accepted");}catch(Exception expected){}
            assertEquals(69000,db.getVehicle().km);assertNotNull(db.getRecord(record.id));
            db.selectVehicle(second);assertTrue(db.getRecords(50).isEmpty());
        }
    }
    @Test public void migratesVersionTwoWithoutLosingRecords()throws Exception{
        File file=c.getDatabasePath("arac_defteri.db");file.getParentFile().mkdirs();
        try(SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(file,null)){
            old.execSQL("CREATE TABLE vehicle (id INTEGER PRIMARY KEY,brand TEXT,model TEXT,year INTEGER,plate TEXT,km INTEGER,fuel_type TEXT)");
            old.execSQL("CREATE TABLE records (id INTEGER PRIMARY KEY AUTOINCREMENT,type TEXT,title TEXT,date TEXT,km INTEGER,cost REAL,detail TEXT,next_date TEXT,next_km INTEGER,attachment TEXT,subtype TEXT,quantity REAL,unit TEXT,unit_price REAL,status TEXT,extra TEXT)");
            old.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT,category TEXT,date TEXT,amount REAL,note TEXT)");
            old.execSQL("CREATE TABLE photos (id INTEGER PRIMARY KEY AUTOINCREMENT,uri TEXT,date TEXT)");
            old.execSQL("INSERT INTO vehicle VALUES(1,'Toyota','Corolla',2021,'',68420,'Benzin')");old.execSQL("INSERT INTO records(type,title,date,km,cost) VALUES('Bakım','Eski kayıt','01.09.2026',68420,2500)");old.setVersion(2);
        }
        try(AppDatabase db=new AppDatabase(c)){db.selectVehicle(1);assertEquals(68420,db.getVehicle().km);assertEquals("Eski kayıt",db.getRecords(10).get(0).title);int second=db.createVehicle("Fiat","Egea",2022,40000,"Benzin");assertEquals(2,second);assertTrue(db.getRecords(10).isEmpty());}
    }
    @Test public void launchThemeScreensAndLongPdf()throws Exception{
        try(AppDatabase db=new AppDatabase(c)){
            db.createVehicle("Toyota","Corolla",2021,68420,"Benzin");AppDatabase.Record r=new AppDatabase.Record();r.type="Bakım";r.title="Periyodik bakım";r.date="15.09.2026";r.km=68420;r.cost=4250;r.nextDate=java.time.LocalDate.now().plusDays(6).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu"));db.addRecord(r);
        }
        c.getSharedPreferences("arac_defteri_prefs",0).edit().putString("theme_mode","light").commit();
        try(ActivityScenario<NextMainActivity> scenario=ActivityScenario.launch(NextMainActivity.class)){
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();shot("home-light.png");
            scenario.onActivity(a->invoke(a,"renderPage",new Class[]{int.class},1));InstrumentationRegistry.getInstrumentation().waitForIdleSync();shot("records-light.png");
            c.getSharedPreferences("arac_defteri_prefs",0).edit().putString("theme_mode","dark").commit();scenario.recreate();InstrumentationRegistry.getInstrumentation().waitForIdleSync();shot("home-dark.png");
            final Exception[] error={null};
            scenario.onActivity(a->{try(AppDatabase db=new AppDatabase(a)){
                StringBuilder note=new StringBuilder();for(int i=0;i<220;i++)note.append("Uzun açıklama ve sayfa geçişi kontrolü. ");
                SharedPreferences prefs=VehiclePreferences.open(a,db.activeId());prefs.edit().putString("cv_note",note.toString()).commit();
                VehicleCvPdf.Result result=VehicleCvPdf.create(a,db,prefs,new ArrayList<>());
                try(ParcelFileDescriptor fd=a.getContentResolver().openFileDescriptor(result.uri,"r");PdfRenderer renderer=new PdfRenderer(fd)){
                    assertTrue(renderer.getPageCount()>1);for(int page:new int[]{0,renderer.getPageCount()-1})try(PdfRenderer.Page p=renderer.openPage(page)){
                        Bitmap image=Bitmap.createBitmap(p.getWidth()*2,p.getHeight()*2,Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);p.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);write(image,"cv-page-"+page+".png");image.recycle();
                    }
                }
            }catch(Exception e){error[0]=e;}});if(error[0]!=null)throw error[0];
        }
    }
    private void invoke(Object o,String name,Class[] types,Object...args){try{java.lang.reflect.Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(o,args);}catch(Exception e){throw new RuntimeException(e);}}
    private void shot(String name)throws Exception{Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);write(image,name);image.recycle();}
    private void write(Bitmap image,String name)throws Exception{File dir=new File(c.getExternalFilesDir(null),"qa");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}}
}
