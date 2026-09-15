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
        public boolean archived;
        public String brand = "", model = "", plate = "", fuelType = "Benzin";
    }

    public static class Record {
        public long id;
        public String type = "", title = "", date = "", detail = "", nextDate = "", attachment = "";
        public String subtype = "", unit = "", status = "", extra = "";
        public int km, nextKm;
        public int vehicleId;
        public boolean completed;
        public String metadata = "{}";
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

    private final Context context;

    public AppDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE vehicle (id INTEGER PRIMARY KEY, brand TEXT, model TEXT, year INTEGER, plate TEXT, km INTEGER, fuel_type TEXT DEFAULT 'Benzin')");
        db.execSQL("CREATE TABLE records (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, title TEXT, date TEXT, km INTEGER, cost REAL, detail TEXT, next_date TEXT, next_km INTEGER, attachment TEXT, subtype TEXT DEFAULT '', quantity REAL DEFAULT 0, unit TEXT DEFAULT '', unit_price REAL DEFAULT 0, status TEXT DEFAULT '', extra TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT, date TEXT, amount REAL, note TEXT)");
        db.execSQL("CREATE TABLE photos (id INTEGER PRIMARY KEY AUTOINCREMENT, uri TEXT, date TEXT)");
        upgradeV3(db);
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
        if (oldVersion < 3) upgradeV3(db);
    }

    private void upgradeV3(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE vehicle ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
        for (String table : new String[]{"records", "expenses", "photos"})
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN vehicle_id INTEGER NOT NULL DEFAULT 1");
        db.execSQL("ALTER TABLE records ADD COLUMN completed INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE records ADD COLUMN metadata TEXT NOT NULL DEFAULT '{}'");
        db.execSQL("CREATE TABLE attachments (id INTEGER PRIMARY KEY AUTOINCREMENT, record_id INTEGER NOT NULL, uri TEXT NOT NULL, label TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE odometer (id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL, km INTEGER NOT NULL, date TEXT NOT NULL, source TEXT NOT NULL)");
        db.execSQL("CREATE INDEX records_vehicle ON records(vehicle_id)");
        db.execSQL("CREATE INDEX attachments_record ON attachments(record_id)");
    }

    public int activeId() { return context.getSharedPreferences("garage", 0).getInt("active", 0); }
    public void selectVehicle(int id) { context.getSharedPreferences("garage", 0).edit().putInt("active", id).commit(); }
    public List<Vehicle> vehicles() {
        List<Vehicle> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,brand,model,year,km,archived FROM vehicle ORDER BY archived,id", null)) {
            while (c.moveToNext()) { Vehicle v = new Vehicle(); v.id=c.getInt(0); v.brand=c.getString(1); v.model=c.getString(2); v.year=c.getInt(3); v.km=c.getInt(4); v.archived=c.getInt(5)!=0; list.add(v); }
        }
        return list;
    }
    public void archiveVehicle(int id, boolean archived) {
        ContentValues v = new ContentValues(); v.put("archived", archived ? 1 : 0);
        getWritableDatabase().update("vehicle", v, "id=?", new String[]{""+id});
    }
    public int createVehicle(String brand, String model, int year, int km, String fuel) {
        ContentValues v = new ContentValues(); v.put("brand", brand); v.put("model", model); v.put("year",year); v.put("km",km); v.put("plate", ""); v.put("fuel_type",fuel);
        int id=(int)getWritableDatabase().insertOrThrow("vehicle",null,v); selectVehicle(id); return id;
    }
    public void updateKm(int km, String source) {
        if(km<0 || km>9999999) throw new IllegalArgumentException("Geçersiz kilometre");
        SQLiteDatabase w=getWritableDatabase(); w.beginTransaction();
        try {
            ContentValues v=new ContentValues(); v.put("km",km); w.update("vehicle",v,"id=?",new String[]{""+activeId()});
            v=new ContentValues(); v.put("vehicle_id",activeId()); v.put("km",km); v.put("date",new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.US).format(new Date())); v.put("source",source); w.insertOrThrow("odometer",null,v);
            w.setTransactionSuccessful();
        } finally {w.endTransaction();}
    }
    public List<String> kmHistory() {
        List<String> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT km,date,source FROM odometer WHERE vehicle_id=? ORDER BY id DESC LIMIT 30",new String[]{""+activeId()})) {
            while(c.moveToNext()) out.add(c.getInt(0)+" km • "+c.getString(1)+" • "+c.getString(2));
        } return out;
    }
    public void completeRecord(long id) {
        ContentValues v=new ContentValues(); v.put("completed",1); getWritableDatabase().update("records",v,"id=? AND vehicle_id=?",new String[]{""+id,""+activeId()});
    }
    public static class Attachment { public long id; public String uri,label; }
    public void addAttachment(long recordId,String uri,String label) {
        if(getRecord(recordId)==null) throw new IllegalArgumentException("Kayıt bulunamadı");
        ContentValues v=new ContentValues();v.put("record_id",recordId);v.put("uri",uri);v.put("label",label);getWritableDatabase().insertOrThrow("attachments",null,v);
    }
    public List<Attachment> attachments(long recordId) {
        List<Attachment> out=new ArrayList<>();
        Record r=getRecord(recordId); if(r==null)return out;
        if(!r.attachment.isEmpty()){Attachment a=new Attachment();a.uri=r.attachment;a.label="Belge";out.add(a);}
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,uri,label FROM attachments WHERE record_id=? ORDER BY id",new String[]{""+recordId})) {
            while(c.moveToNext()){Attachment a=new Attachment();a.id=c.getLong(0);a.uri=c.getString(1);a.label=c.getString(2);out.add(a);}
        }return out;
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
        Cursor c = getReadableDatabase().rawQuery("SELECT id,brand,model,year,plate,km,COALESCE(fuel_type,'Benzin') FROM vehicle WHERE id=? LIMIT 1", new String[]{""+activeId()});
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
        getWritableDatabase().update("vehicle", v, "id=?", new String[]{""+activeId()});
    }

    public long addRecord(Record r) {
        return getWritableDatabase().insert("records", null, recordValues(r, false));
    }

    public void updateRecord(Record r) {
        getWritableDatabase().update("records", recordValues(r, true), "id=? AND vehicle_id=?", new String[]{String.valueOf(r.id),""+activeId()});
    }

    private ContentValues recordValues(Record r, boolean keepAttachment) {
        ContentValues v = new ContentValues();
        v.put("vehicle_id", activeId()); v.put("completed",r.completed?1:0); v.put("metadata",r.metadata);
        v.put("type", r.type); v.put("title", r.title); v.put("date", r.date); v.put("km", r.km); v.put("cost", r.cost);
        v.put("detail", r.detail); v.put("next_date", r.nextDate); v.put("next_km", r.nextKm); v.put("subtype", r.subtype);
        v.put("quantity", r.quantity); v.put("unit", r.unit); v.put("unit_price", r.unitPrice); v.put("status", r.status); v.put("extra", r.extra);
        if (!keepAttachment) v.put("attachment", nz(r.attachment));
        return v;
    }

    public Record getRecord(long id) {
        Cursor c = getReadableDatabase().rawQuery(recordSelect() + " WHERE id=? AND vehicle_id=? LIMIT 1", new String[]{String.valueOf(id),""+activeId()});
        Record r = null;
        if (c.moveToFirst()) r = readRecord(c);
        c.close();
        return r;
    }

    public void deleteRecord(long id) {
        if(getRecord(id)==null)return;
        getWritableDatabase().delete("attachments","record_id=?",new String[]{""+id});
        getWritableDatabase().delete("records", "id=? AND vehicle_id=?", new String[]{String.valueOf(id),""+activeId()});
    }

    public void setRecordAttachment(long id, String uri) {
        ContentValues v = new ContentValues(); v.put("attachment", uri);
        getWritableDatabase().update("records", v, "id=? AND vehicle_id=?", new String[]{String.valueOf(id),""+activeId()});
    }

    public List<Record> getRecords(int limit) {
        return queryRecords("", null, " ORDER BY id DESC LIMIT " + limit);
    }

    public List<Record> getRecordsByType(String type) {
        return queryRecords(" WHERE type=?", new String[]{type}, " ORDER BY id DESC");
    }

    public List<Record> getUpcomingRecords(int limit) {
        List<Record> list=queryRecords(" WHERE completed=0 AND (next_date<>'' OR next_km>0)",null,"");
        list.removeIf(r->!RecordRules.pending(r));
        list.sort((a,b)->Long.compare(RecordRules.priority(a,getVehicle().km),RecordRules.priority(b,getVehicle().km)));
        return new ArrayList<>(list.subList(0,Math.min(limit,list.size())));
    }

    private List<Record> queryRecords(String where, String[] args, String tail) {
        List<Record> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(recordSelect() + (where.isEmpty()?" WHERE ":where+" AND ") + "vehicle_id="+activeId()+tail, args);
        while (c.moveToNext()) out.add(readRecord(c));
        c.close();
        return out;
    }

    private String recordSelect() {
        return "SELECT id,type,title,date,km,cost,detail,next_date,next_km,attachment,COALESCE(subtype,''),COALESCE(quantity,0),COALESCE(unit,''),COALESCE(unit_price,0),COALESCE(status,''),COALESCE(extra,''),vehicle_id,completed,metadata FROM records";
    }

    private Record readRecord(Cursor c) {
        Record r = new Record();
        r.id=c.getLong(0); r.type=nz(c.getString(1)); r.title=nz(c.getString(2)); r.date=nz(c.getString(3)); r.km=c.getInt(4); r.cost=c.getDouble(5);
        r.detail=nz(c.getString(6)); r.nextDate=nz(c.getString(7)); r.nextKm=c.getInt(8); r.attachment=nz(c.getString(9)); r.subtype=nz(c.getString(10));
        r.quantity=c.getDouble(11); r.unit=nz(c.getString(12)); r.unitPrice=c.getDouble(13); r.status=nz(c.getString(14)); r.extra=nz(c.getString(15)); r.vehicleId=c.getInt(16); r.completed=c.getInt(17)!=0; r.metadata=nz(c.getString(18));
        return r;
    }

    public long addExpense(String category, String date, double amount, String note) {
        ContentValues v = new ContentValues(); v.put("vehicle_id",activeId()); v.put("category", category); v.put("date", date); v.put("amount", amount); v.put("note", note);
        return getWritableDatabase().insert("expenses", null, v);
    }

    public void updateExpense(Expense e) {
        ContentValues v = new ContentValues(); v.put("category", e.category); v.put("date", e.date); v.put("amount", e.amount); v.put("note", e.note);
        getWritableDatabase().update("expenses", v, "id=? AND vehicle_id=?", new String[]{String.valueOf(e.id),""+activeId()});
    }

    public void deleteExpense(long id) {
        getWritableDatabase().delete("expenses", "id=? AND vehicle_id=?", new String[]{String.valueOf(id),""+activeId()});
    }

    public List<Expense> getExpenses() {
        List<Expense> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,category,date,amount,note FROM expenses WHERE vehicle_id="+activeId()+" ORDER BY id DESC", null);
        while (c.moveToNext()) {
            Expense e = new Expense(); e.id=c.getLong(0); e.category=nz(c.getString(1)); e.date=nz(c.getString(2)); e.amount=c.getDouble(3); e.note=nz(c.getString(4)); out.add(e);
        }
        c.close(); return out;
    }

    public double getCurrentMonthExpenseTotal() {
        double total = 0;
        String suffix = new SimpleDateFormat("MM.yyyy", Locale.getDefault()).format(new Date());
        Cursor c = getReadableDatabase().rawQuery("SELECT amount,date FROM expenses WHERE vehicle_id="+activeId(), null);
        while(c.moveToNext()) if(nz(c.getString(1)).endsWith(suffix)) total += c.getDouble(0); c.close();
        Cursor r = getReadableDatabase().rawQuery("SELECT cost,date FROM records WHERE cost>0 AND NOT (type='Vergi' AND status='Ödenmedi') AND vehicle_id="+activeId(), null);
        while(r.moveToNext()) if(nz(r.getString(1)).endsWith(suffix)) total += r.getDouble(0); r.close();
        return total;
    }

    public double getExpenseTotal() {
        double total=0;
        Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE vehicle_id="+activeId(),null); c.moveToFirst(); total+=c.getDouble(0); c.close();
        Cursor r=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(cost),0) FROM records WHERE NOT (type='Vergi' AND status='Ödenmedi') AND vehicle_id="+activeId(),null); r.moveToFirst(); total+=r.getDouble(0); r.close();
        return total;
    }

    public void addPhoto(String uri, String date) {
        ContentValues v = new ContentValues(); v.put("vehicle_id",activeId()); v.put("uri", uri); v.put("date", date); getWritableDatabase().insert("photos", null, v);
    }

    public void deletePhoto(long id) { getWritableDatabase().delete("photos", "id=? AND vehicle_id=?", new String[]{String.valueOf(id),""+activeId()}); }

    public List<Photo> getPhotos() {
        List<Photo> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,uri,date FROM photos WHERE vehicle_id="+activeId()+" ORDER BY id DESC", null);
        while(c.moveToNext()) { Photo p=new Photo(); p.id=c.getLong(0); p.uri=nz(c.getString(1)); p.date=nz(c.getString(2)); out.add(p); }
        c.close(); return out;
    }

    public int countRecordsByType(String type) {
        Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM records WHERE type=? AND vehicle_id=?",new String[]{type,""+activeId()}); c.moveToFirst(); int n=c.getInt(0); c.close(); return n;
    }

    public int countAllRecords() {
        Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM records WHERE vehicle_id="+activeId(),null); c.moveToFirst(); int n=c.getInt(0); c.close(); return n;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
