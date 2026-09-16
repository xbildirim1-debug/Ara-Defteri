package com.aracdefteri.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "arac_defteri.db";
    private static final int DB_VERSION = 3;

    public static class Vehicle {
        public int id, year, km;
        public String brand = "", model = "", plate = "", fuelType = "Benzin";
    }

    public static class Record {
        public long id;
        public String type = "", title = "", date = "", detail = "", nextDate = "", attachment = "";
        public String subtype = "", unit = "", status = "", extra = "";
        public int km, nextKm;
        public double cost, quantity, unitPrice;
    }

    public static class Expense {
        public long id;
        public String category = "", date = "", note = "";
        public double amount;
    }

    public static class Photo {
        public long id;
        public String uri = "", date = "";
    }

    public static class RecordPhoto {
        public long id, recordId;
        public String uri = "", date = "";
    }

    public AppDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE vehicle (id INTEGER PRIMARY KEY, brand TEXT, model TEXT, year INTEGER, plate TEXT, km INTEGER, fuel_type TEXT DEFAULT 'Benzin')");
        db.execSQL("CREATE TABLE records (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, title TEXT, date TEXT, km INTEGER, cost REAL, detail TEXT, next_date TEXT, next_km INTEGER, attachment TEXT, subtype TEXT DEFAULT '', quantity REAL DEFAULT 0, unit TEXT DEFAULT '', unit_price REAL DEFAULT 0, status TEXT DEFAULT '', extra TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT, date TEXT, amount REAL, note TEXT)");
        db.execSQL("CREATE TABLE photos (id INTEGER PRIMARY KEY AUTOINCREMENT, uri TEXT, date TEXT)");
        db.execSQL("CREATE TABLE record_photos (id INTEGER PRIMARY KEY AUTOINCREMENT, record_id INTEGER NOT NULL, uri TEXT NOT NULL, date TEXT DEFAULT '')");
        db.execSQL("CREATE INDEX idx_record_photos_record_id ON record_photos(record_id)");
        ContentValues blankVehicle = new ContentValues();
        blankVehicle.put("id", 1);
        blankVehicle.put("brand", "");
        blankVehicle.put("model", "");
        blankVehicle.put("year", 0);
        blankVehicle.put("plate", "");
        blankVehicle.put("km", 0);
        blankVehicle.put("fuel_type", "Benzin");
        db.insert("vehicle", null, blankVehicle);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            addColumn(db, "vehicle", "fuel_type", "TEXT DEFAULT 'Benzin'");
            addColumn(db, "records", "subtype", "TEXT DEFAULT ''");
            addColumn(db, "records", "quantity", "REAL DEFAULT 0");
            addColumn(db, "records", "unit", "TEXT DEFAULT ''");
            addColumn(db, "records", "unit_price", "REAL DEFAULT 0");
            addColumn(db, "records", "status", "TEXT DEFAULT ''");
            addColumn(db, "records", "extra", "TEXT DEFAULT ''");
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS record_photos (id INTEGER PRIMARY KEY AUTOINCREMENT, record_id INTEGER NOT NULL, uri TEXT NOT NULL, date TEXT DEFAULT '')");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_record_photos_record_id ON record_photos(record_id)");
        }
    }

    private void addColumn(SQLiteDatabase db, String table, String column, String definition) {
        try { db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition); }
        catch (Exception ignored) { }
    }

    public void seedDemoIfNeeded() {
        SQLiteDatabase w = getWritableDatabase();
        Cursor c = w.rawQuery("SELECT COUNT(*) FROM vehicle", null);
        c.moveToFirst();
        int count = c.getInt(0);
        c.close();
        if (count > 0) return;

        ContentValues vehicle = new ContentValues();
        vehicle.put("id", 1);
        vehicle.put("brand", "Toyota");
        vehicle.put("model", "Corolla Hybrid");
        vehicle.put("year", 2021);
        vehicle.put("plate", "34 DEM 2026");
        vehicle.put("km", 68420);
        vehicle.put("fuel_type", "Hibrit / Benzin");
        w.insert("vehicle", null, vehicle);

        Record maintenance = new Record();
        maintenance.type = "Bakım"; maintenance.title = "Periyodik bakım"; maintenance.date = "02.09.2026";
        maintenance.km = 67120; maintenance.cost = 4250; maintenance.subtype = "Periyodik bakım";
        maintenance.detail = "Motor yağı, yağ filtresi ve polen filtresi değişti."; maintenance.nextDate = "02.09.2027"; maintenance.nextKm = 77120;
        addRecord(maintenance);

        Record expertise = new Record();
        expertise.type = "Ekspertiz"; expertise.title = "Yıllık genel kontrol"; expertise.date = "17.08.2026";
        expertise.km = 65840; expertise.cost = 1500; expertise.subtype = "Genel ekspertiz";
        expertise.detail = "Kaporta, fren ve alt takım kontrol edildi. Demo kayıt.";
        addRecord(expertise);

        Record damage = new Record();
        damage.type = "Hasar"; damage.title = "Sağ arka tampon çizik onarımı"; damage.date = "21.06.2026";
        damage.km = 63210; damage.cost = 2800; damage.subtype = "Çizik / kozmetik"; damage.status = "Onarıldı";
        damage.detail = "Lokal boya uygulandı. Demo kayıt.";
        addRecord(damage);

        Record inspection = new Record();
        inspection.type = "Muayene"; inspection.title = "Periyodik muayene"; inspection.date = "10.04.2026";
        inspection.km = 60130; inspection.subtype = "Kusursuz"; inspection.detail = "Kusursuz geçti. Demo kayıt."; inspection.nextDate = "10.04.2028";
        addRecord(inspection);

        addExpense("Yıkama", "08.09.2026", 500, "İç-dış yıkama");
        addExpense("Otopark", "05.09.2026", 220, "Şehir merkezi");
    }

    public Vehicle getVehicle() {
        Vehicle v = new Vehicle();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,brand,model,year,plate,km,COALESCE(fuel_type,'Benzin') FROM vehicle LIMIT 1", null);
        if (c.moveToFirst()) {
            v.id = c.getInt(0); v.brand = nz(c.getString(1)); v.model = nz(c.getString(2)); v.year = c.getInt(3);
            v.plate = nz(c.getString(4)); v.km = c.getInt(5); v.fuelType = nz(c.getString(6));
            if (v.fuelType.isEmpty()) v.fuelType = "Benzin";
        }
        c.close();
        return v;
    }

    public void updateVehicle(String brand, String model, int year, String plate, int km, String fuelType) {
        ContentValues v = new ContentValues();
        v.put("brand", brand); v.put("model", model); v.put("year", year); v.put("plate", plate); v.put("km", km); v.put("fuel_type", fuelType);
        SQLiteDatabase w = getWritableDatabase();
        int changed = w.update("vehicle", v, "id=1", null);
        if (changed == 0) { v.put("id", 1); w.insert("vehicle", null, v); }
    }

    public boolean clearLegacyDemoIfPresent() {
        Vehicle v = getVehicle();
        if (!("Toyota".equals(v.brand) && "Corolla Hybrid".equals(v.model) && v.year == 2021)) return false;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM records WHERE title IN ('Periyodik bakım','Yıllık genel kontrol','Sağ arka tampon çizik onarımı','Periyodik muayene')", null);
        c.moveToFirst();
        int seeded = c.getInt(0);
        c.close();
        if (seeded < 2) return false;
        SQLiteDatabase w = getWritableDatabase();
        w.delete("record_photos", null, null);
        w.delete("records", null, null);
        w.delete("expenses", null, null);
        w.delete("photos", null, null);
        updateVehicle("", "", 0, "", 0, "Benzin");
        return true;
    }

    public long addRecord(Record r) {
        return getWritableDatabase().insert("records", null, recordValues(r, false));
    }

    public void updateRecord(Record r) {
        getWritableDatabase().update("records", recordValues(r, true), "id=?", new String[]{String.valueOf(r.id)});
    }

    private ContentValues recordValues(Record r, boolean keepAttachment) {
        ContentValues v = new ContentValues();
        v.put("type", r.type); v.put("title", r.title); v.put("date", r.date); v.put("km", r.km); v.put("cost", r.cost);
        v.put("detail", r.detail); v.put("next_date", r.nextDate); v.put("next_km", r.nextKm); v.put("subtype", r.subtype);
        v.put("quantity", r.quantity); v.put("unit", r.unit); v.put("unit_price", r.unitPrice); v.put("status", r.status); v.put("extra", r.extra);
        if (!keepAttachment) v.put("attachment", nz(r.attachment));
        return v;
    }

    public Record getRecord(long id) {
        Cursor c = getReadableDatabase().rawQuery(recordSelect() + " WHERE id=? LIMIT 1", new String[]{String.valueOf(id)});
        Record r = null;
        if (c.moveToFirst()) r = readRecord(c);
        c.close();
        return r;
    }

    public void deleteRecord(long id) {
        SQLiteDatabase w = getWritableDatabase();
        w.delete("record_photos", "record_id=?", new String[]{String.valueOf(id)});
        w.delete("records", "id=?", new String[]{String.valueOf(id)});
    }

    public void setRecordAttachment(long id, String uri) {
        ContentValues v = new ContentValues(); v.put("attachment", uri);
        getWritableDatabase().update("records", v, "id=?", new String[]{String.valueOf(id)});
    }

    public List<Record> getRecords(int limit) {
        return queryRecords("", null, " ORDER BY id DESC LIMIT " + limit);
    }

    public List<Record> getRecordsByType(String type) {
        return queryRecords(" WHERE type=?", new String[]{type}, " ORDER BY id DESC");
    }

    public List<Record> getUpcomingRecords(int limit) {
        return queryRecords(" WHERE next_date<>'' OR next_km>0", null, " ORDER BY id DESC LIMIT " + limit);
    }

    private List<Record> queryRecords(String where, String[] args, String tail) {
        List<Record> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(recordSelect() + where + tail, args);
        while (c.moveToNext()) out.add(readRecord(c));
        c.close();
        return out;
    }

    private String recordSelect() {
        return "SELECT id,type,title,date,km,cost,detail,next_date,next_km,attachment,COALESCE(subtype,''),COALESCE(quantity,0),COALESCE(unit,''),COALESCE(unit_price,0),COALESCE(status,''),COALESCE(extra,'') FROM records";
    }

    private Record readRecord(Cursor c) {
        Record r = new Record();
        r.id=c.getLong(0); r.type=nz(c.getString(1)); r.title=nz(c.getString(2)); r.date=nz(c.getString(3)); r.km=c.getInt(4); r.cost=c.getDouble(5);
        r.detail=nz(c.getString(6)); r.nextDate=nz(c.getString(7)); r.nextKm=c.getInt(8); r.attachment=nz(c.getString(9)); r.subtype=nz(c.getString(10));
        r.quantity=c.getDouble(11); r.unit=nz(c.getString(12)); r.unitPrice=c.getDouble(13); r.status=nz(c.getString(14)); r.extra=nz(c.getString(15));
        return r;
    }

    public long addExpense(String category, String date, double amount, String note) {
        ContentValues v = new ContentValues(); v.put("category", category); v.put("date", date); v.put("amount", amount); v.put("note", note);
        return getWritableDatabase().insert("expenses", null, v);
    }

    public void updateExpense(Expense e) {
        ContentValues v = new ContentValues(); v.put("category", e.category); v.put("date", e.date); v.put("amount", e.amount); v.put("note", e.note);
        getWritableDatabase().update("expenses", v, "id=?", new String[]{String.valueOf(e.id)});
    }

    public void deleteExpense(long id) {
        getWritableDatabase().delete("expenses", "id=?", new String[]{String.valueOf(id)});
    }

    public List<Expense> getExpenses() {
        List<Expense> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,category,date,amount,note FROM expenses ORDER BY id DESC", null);
        while (c.moveToNext()) {
            Expense e = new Expense(); e.id=c.getLong(0); e.category=nz(c.getString(1)); e.date=nz(c.getString(2)); e.amount=c.getDouble(3); e.note=nz(c.getString(4)); out.add(e);
        }
        c.close(); return out;
    }

    public double getCurrentMonthExpenseTotal() {
        double total = 0;
        String suffix = new SimpleDateFormat("MM.yyyy", Locale.getDefault()).format(new Date());
        Cursor c = getReadableDatabase().rawQuery("SELECT amount,date FROM expenses", null);
        while(c.moveToNext()) if(nz(c.getString(1)).endsWith(suffix)) total += c.getDouble(0); c.close();
        Cursor r = getReadableDatabase().rawQuery("SELECT cost,date FROM records WHERE cost>0", null);
        while(r.moveToNext()) if(nz(r.getString(1)).endsWith(suffix)) total += r.getDouble(0); r.close();
        return total;
    }

    public double getExpenseTotal() {
        double total=0;
        Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM expenses",null); c.moveToFirst(); total+=c.getDouble(0); c.close();
        Cursor r=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(cost),0) FROM records",null); r.moveToFirst(); total+=r.getDouble(0); r.close();
        return total;
    }

    public void addPhoto(String uri, String date) {
        ContentValues v = new ContentValues(); v.put("uri", uri); v.put("date", date); getWritableDatabase().insert("photos", null, v);
    }

    public void deletePhoto(long id) { getWritableDatabase().delete("photos", "id=?", new String[]{String.valueOf(id)}); }

    public List<Photo> getPhotos() {
        List<Photo> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,uri,date FROM photos ORDER BY id DESC", null);
        while(c.moveToNext()) { Photo p=new Photo(); p.id=c.getLong(0); p.uri=nz(c.getString(1)); p.date=nz(c.getString(2)); out.add(p); }
        c.close(); return out;
    }

    public long addRecordPhoto(long recordId, String uri, String date) {
        ContentValues v = new ContentValues();
        v.put("record_id", recordId); v.put("uri", uri); v.put("date", date);
        return getWritableDatabase().insert("record_photos", null, v);
    }

    public void deleteRecordPhoto(long id) {
        getWritableDatabase().delete("record_photos", "id=?", new String[]{String.valueOf(id)});
    }

    public int countRecordPhotos(long recordId) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM record_photos WHERE record_id=?", new String[]{String.valueOf(recordId)});
        c.moveToFirst(); int n = c.getInt(0); c.close(); return n;
    }

    public List<RecordPhoto> getRecordPhotos(long recordId) {
        List<RecordPhoto> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,record_id,uri,date FROM record_photos WHERE record_id=? ORDER BY id ASC", new String[]{String.valueOf(recordId)});
        while (c.moveToNext()) {
            RecordPhoto photo = new RecordPhoto();
            photo.id = c.getLong(0); photo.recordId = c.getLong(1); photo.uri = nz(c.getString(2)); photo.date = nz(c.getString(3));
            out.add(photo);
        }
        c.close(); return out;
    }

    public int countRecordsByType(String type) {
        Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM records WHERE type=?",new String[]{type}); c.moveToFirst(); int n=c.getInt(0); c.close(); return n;
    }

    public int countAllRecords() {
        Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM records",null); c.moveToFirst(); int n=c.getInt(0); c.close(); return n;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
