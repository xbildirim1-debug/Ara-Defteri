package com.aracdefteri.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** User initiated, complete portable backup. Import is validated before replacing data. */
final class BackupManager {
    private static final String[] TABLES={"vehicle","records","expenses","photos","attachments","odometer"};
    private static final long MAX_BYTES=2L*1024*1024*1024;
    static void exportTo(Context c,AppDatabase db,Uri dest)throws Exception {
        // Old releases stored external document references. Materialize them first.
        materialize(c,db);
        JSONObject root=new JSONObject().put("format","arac-defteri").put("version",1);
        JSONObject tables=new JSONObject();
        for(String table:TABLES){
            JSONArray rows=new JSONArray();
            try(Cursor cur=db.getReadableDatabase().rawQuery("SELECT * FROM "+table,null)){
                while(cur.moveToNext()){
                    JSONObject row=new JSONObject();
                    for(int i=0;i<cur.getColumnCount();i++){
                        Object v=cur.isNull(i)?JSONObject.NULL:cur.getType(i)==Cursor.FIELD_TYPE_INTEGER?cur.getLong(i):cur.getType(i)==Cursor.FIELD_TYPE_FLOAT?cur.getDouble(i):cur.getString(i);
                        row.put(cur.getColumnName(i),v);
                    }rows.put(row);
                }
            }tables.put(table,rows);
        }root.put("tables",tables);
        JSONObject prefs=new JSONObject();
        List<String> names=new ArrayList<>(Arrays.asList("garage","arac_defteri_prefs"));
        for(AppDatabase.Vehicle v:db.vehicles())names.add("vehicle_"+v.id);
        for(String name:names){JSONObject values=new JSONObject();
            for(Map.Entry<String,?> e:c.getSharedPreferences(name,0).getAll().entrySet()){
                Object value=e.getValue(); if(value instanceof Set)continue;
                values.put(e.getKey(),new JSONObject().put("type",value.getClass().getSimpleName()).put("value",value));
            }prefs.put(name,values);
        }root.put("prefs",prefs);
        try(OutputStream output=c.getContentResolver().openOutputStream(dest,"wt")){
            if(output==null)throw new IOException("Yedek dosyası açılamadı");
            try(ZipOutputStream zip=new ZipOutputStream(output)){
                zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(root.toString().getBytes("UTF-8"));zip.closeEntry();
                File[] files=new File(c.getFilesDir(),"media").listFiles();
                if(files!=null)for(File file:files)if(file.isFile()){
                    zip.putNextEntry(new ZipEntry("media/"+file.getName()));try(InputStream in=new FileInputStream(file)){copy(in,zip,MAX_BYTES);}zip.closeEntry();
                }
            }
        }
    }
    private static void materialize(Context c,AppDatabase db)throws Exception {
        SQLiteDatabase w=db.getWritableDatabase();
        for(String[] pair:new String[][]{{"photos","uri"},{"records","attachment"},{"attachments","uri"}}){
            try(Cursor cur=w.rawQuery("SELECT id,"+pair[1]+" FROM "+pair[0],null)){
                while(cur.moveToNext()){
                    String value=cur.getString(1);if(value==null||value.isEmpty()||MediaFiles.owned(c,value))continue;
                    Uri uri=MediaFiles.copy(c,Uri.parse(value));ContentValues cv=new ContentValues();cv.put(pair[1],uri.toString());
                    w.update(pair[0],cv,"id=?",new String[]{cur.getString(0)});
                }
            }
        }
        for(AppDatabase.Vehicle vehicle:db.vehicles()){
            SharedPreferences p=VehiclePreferences.open(c,vehicle.id);String value=p.getString("vehicle_photo_uri","");
            if(!value.isEmpty()&&!MediaFiles.owned(c,value))p.edit().putString("vehicle_photo_uri",MediaFiles.copy(c,Uri.parse(value)).toString()).commit();
        }
    }
    static void restore(Context c,AppDatabase db,Uri source)throws Exception {
        File stage=new File(c.getCacheDir(),"restore-"+UUID.randomUUID());stage.mkdirs();
        File stagedMedia=new File(stage,"media");stagedMedia.mkdirs();
        File media=new File(c.getFilesDir(),"media"),old=new File(c.getFilesDir(),"media-old-"+UUID.randomUUID());
        boolean swapped=false,success=false;
        try {
            long total=0;Set<String> seen=new HashSet<>();int files=0;
            try(InputStream input=c.getContentResolver().openInputStream(source);ZipInputStream zip=new ZipInputStream(input)){
                ZipEntry e;while((e=zip.getNextEntry())!=null){
                    String name=e.getName();
                    if(++files>20000||!seen.add(name)||!(name.equals("manifest.json")||name.matches("media/[A-Za-z0-9._-]+")))throw new IOException("Geçersiz yedek içeriği");
                    File file=new File(stage,name);if(!file.getCanonicalPath().startsWith(stage.getCanonicalPath()+File.separator))throw new IOException("Geçersiz dosya yolu");
                    try(OutputStream out=new FileOutputStream(file)){total+=copy(zip,out,Math.min(MAX_BYTES-total,name.equals("manifest.json")?64L*1024*1024:100L*1024*1024));}
                    zip.closeEntry();
                }
            }
            JSONObject root=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(stage,"manifest.json").toPath()),"UTF-8"));
            if(!"arac-defteri".equals(root.optString("format"))||root.optInt("version")!=1)throw new IOException("Bu Araç Defteri yedeği desteklenmiyor");
            JSONObject tables=root.getJSONObject("tables"),prefs=root.getJSONObject("prefs");
            for(String table:TABLES)tables.getJSONArray(table);
            Iterator<String> names=prefs.keys();while(names.hasNext()){
                String name=names.next();if(!name.matches("vehicle_[0-9]+|garage|arac_defteri_prefs"))throw new IOException("Geçersiz ayar grubu");
            }
            // Validate every managed URI has a corresponding file before touching live data.
            for(String table:TABLES){JSONArray rows=tables.getJSONArray(table);for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);for(String key:new String[]{"uri","attachment"})checkMedia(c,stage,row.optString(key,""));
            }}
            names=prefs.keys();while(names.hasNext()){
                JSONObject values=prefs.getJSONObject(names.next());JSONObject photo=values.optJSONObject("vehicle_photo_uri");if(photo!=null)checkMedia(c,stage,photo.optString("value",""));
            }
            SQLiteDatabase w=db.getWritableDatabase();w.beginTransaction();
            try{
                for(String table:TABLES)w.delete(table,null,null);
                for(String table:TABLES){
                    Set<String> allowed=new HashSet<>();try(Cursor cur=w.rawQuery("PRAGMA table_info("+table+")",null)){while(cur.moveToNext())allowed.add(cur.getString(1));}
                    JSONArray rows=tables.getJSONArray(table);
                    for(int i=0;i<rows.length();i++){
                        JSONObject row=rows.getJSONObject(i);ContentValues cv=new ContentValues();Iterator<String> keys=row.keys();
                        while(keys.hasNext()){String key=keys.next();if(!allowed.contains(key))throw new IOException("Yedek sürümü uyumsuz");Object v=row.get(key);
                            if(v==JSONObject.NULL)cv.putNull(key);else if(v instanceof Integer||v instanceof Long)cv.put(key,((Number)v).longValue());else if(v instanceof Number)cv.put(key,((Number)v).doubleValue());else cv.put(key,v.toString());
                        }w.insertOrThrow(table,null,cv);
                    }
                }
                for(String table:new String[]{"records","expenses","photos","odometer"})try(Cursor cur=w.rawQuery("SELECT COUNT(*) FROM "+table+" WHERE vehicle_id NOT IN (SELECT id FROM vehicle)",null)){cur.moveToFirst();if(cur.getInt(0)>0)throw new IOException("Yedekte araç ilişkisi eksik");}
                if(media.exists()&&!media.renameTo(old))throw new IOException("Mevcut fotoğraflar taşınamadı");
                if(!stagedMedia.renameTo(media)){old.renameTo(media);throw new IOException("Fotoğraflar geri yüklenemedi");}
                swapped=true;w.setTransactionSuccessful();
            }finally{w.endTransaction();}
            names=prefs.keys();while(names.hasNext()){
                String name=names.next();SharedPreferences.Editor edit=c.getSharedPreferences(name,0).edit().clear();JSONObject values=prefs.getJSONObject(name);Iterator<String> keys=values.keys();
                while(keys.hasNext()){String key=keys.next();JSONObject item=values.getJSONObject(key);Object val=item.get("value");
                    switch(item.getString("type")){case "Integer":edit.putInt(key,((Number)val).intValue());break;case "Long":edit.putLong(key,((Number)val).longValue());break;case "Float":edit.putFloat(key,((Number)val).floatValue());break;case "Boolean":edit.putBoolean(key,(Boolean)val);break;default:edit.putString(key,val.toString());}
                }if(!edit.commit())throw new IOException("Ayarlar geri yüklenemedi");
            }
            success=true;
        }finally{
            if(swapped&&!success){/* Keep old media as an additional recovery copy; never delete it on failure. */}
            if(success)deleteTree(old);deleteTree(stage);
        }
    }
    private static void checkMedia(Context c,File stage,String value)throws IOException {
        if(value.isEmpty())return;
        if(!MediaFiles.owned(c,value))throw new IOException("Yedek dış dosya bağlantısı içeriyor");
        String name=Uri.parse(value).getLastPathSegment();if(name==null||!name.matches("[A-Za-z0-9._-]+")||!new File(stage,"media/"+name).isFile())throw new IOException("Yedekte fotoğraf veya belge eksik");
    }
    private static long copy(InputStream in,OutputStream out,long max)throws IOException{byte[] b=new byte[32768];long count=0;int n;while((n=in.read(b))!=-1){count+=n;if(count>max)throw new IOException("Yedek boyutu sınırı aşıldı");out.write(b,0,n);}return count;}
    private static void deleteTree(File f){File[] children=f.listFiles();if(children!=null)for(File child:children)deleteTree(child);f.delete();}
}
