package com.aracdefteri.app;
import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.provider.Settings;
import android.widget.*;
import java.util.*;
import java.text.SimpleDateFormat;

/** Test-stage OBD dashboard. Values are empty until a real ECU response is received. */
public final class ObdView extends LinearLayout implements ObdBluetooth.Listener {
 private final Activity activity;private final int fg,bg,accent;private final ObdBluetooth connection;
 private final TextView status,stamp,codes;private final Map<Integer,TextView> fields=new LinkedHashMap<>();
 private Button update;private long timestamp;private Double odometer;private boolean attached=true;
 public ObdView(Activity activity,int fg,int bg,int accent){
  super(activity);this.activity=activity;this.fg=fg;this.bg=bg;this.accent=accent;setOrientation(VERTICAL);connection=new ObdBluetooth(this);
  label("OBD · Test aşamasındadır",23,true);label("Bluetooth ELM327 uyumlu adaptör • Gerçek araç testi bekleniyor",12,false);
  status=label("Bağlantı yok. Değerler araçtan okununca gösterilecek.",14,true);
  button("Bluetooth cihazı seç ve bağlan",this::chooseDevice);
  button("Bluetooth ayarları / eşleştirme",()->activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
  button("Ölçümleri ve arıza kodlarını yenile",()->{odometer=null;update.setEnabled(false);connection.refresh();});
  button("Bağlantıyı kes",()->{connection.disconnect();invalidate();status.setText("Bağlantı kesildi.");});
  stamp=label("Henüz ölçüm alınmadı",12,false);
  label("Araç bilgilerini güncelle",19,true);
  label("Yalnız ECU toplam kilometre verisi aktarılır. Hız, yolculuk ve arıza sonrası mesafe toplam km değildir. Canlı sensörler kayıtlı araç bilgilerini değiştirmez.",12,false);
  update=button("Toplam kilometreyi karşılaştır ve güncelle",this::updateKm);update.setEnabled(false);
  label("Gösterge uyarıları",19,true);
  button("Kırmızı yağ basıncı lambası yanıyor",()->new AlertDialog.Builder(activity).setTitle("Motor çalışırken kırmızı yağ basıncı uyarısı")
   .setMessage("Güvenli yerde dur ve motoru kapat. Yağ seviyesini kullanım kılavuzuna göre kontrol et; uyarı sürüyorsa aracı kullanma, yol yardımı/servis iste. OBD kodu olmaması yağ basıncının normal olduğunu göstermez. Yağ sıcaklığı, yağ basıncı veya yağ seviyesi değildir.").setPositiveButton("Anladım",null).show());
  button("Motor arıza lambası yanıyor",()->new AlertDialog.Builder(activity).setTitle("Motor arıza lambası")
   .setMessage("Kodları oku: açıklamalar olası nedenleri gösterir, kesin parça teşhisi değildir. Lamba yanıp sönüyor veya motor belirgin titriyorsa güvenli yerde durup servis desteği al. Standart OBD lamba verisi yanıp sönmeyi her zaman bildirmez.").setPositiveButton("Anladım",null).show());
  label("Araçtan okunabilen standart veriler",19,true);
  for(ObdProtocol.Metric metric:ObdProtocol.METRICS)fields.put(metric.pid,label(metric.title+": —",14,false));
  label("Arıza kodları",19,true);codes=label("Henüz taranmadı. Kod bulunmaması araçta arıza olmadığını kanıtlamaz.",13,false);
  label("Markaya ve modele özel veriler",19,true);
  label("ABS/ESP, airbag, TPMS, şanzıman üniteleri, DPF doluluğu, AdBlue, gerçek yağ basıncı, servis sayacı ve hibrit batarya sağlığı üreticiye özel erişim isteyebilir. Bu test sürümünde bu modüller taranmıyor; veri uydurulmaz. Yağ basıncı uyarısını yukarıdaki düğmeyle ayrıca değerlendirebilirsin.",13,false);
  label("Uyumluluk ve açıklamalar",19,true);
  label("Standart kodlar ortak olabilir; üreticiye özel kodlar marka/model/yıl/motor ve kontrol ünitesiyle doğrulanmalıdır. Bilinmeyen kodun açıklaması tahmin edilmez. Bu sürüm Bluetooth Classic SPP kullanır; BLE ve Wi-Fi adaptörleri için farklı bağlantı gerekir. Kod silme, kodlama ve motor beynine ayar yazma yapılmaz.",13,false);
  button("OBD üreticisinin uyumluluk açıklaması",()->open("https://support.obdlink.com/support/solutions/articles/43000705533-are-enhanced-diagnostics-available-for-my-vehicle-"));
 }
 private void open(String url){activity.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}
 private TextView label(String s,int size,boolean bold){TextView t=new TextView(activity);t.setText(s);t.setTextSize(size);t.setTextColor(fg);t.setPadding(8,16,8,12);if(bold)t.setTypeface(null,1);addView(t);return t;}
 private Button button(String s,Runnable r){Button b=new Button(activity);b.setText(s);b.setAllCaps(false);b.setTextColor(fg);b.setOnClickListener(v->r.run());addView(b,new LayoutParams(-1,-2));return b;}
 private void chooseDevice(){
  if(Build.VERSION.SDK_INT>=31&&activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},8120);status.setText("Yakındaki cihazlar iznini ver, ardından cihaz seç düğmesine tekrar bas.");return;}
  try{BluetoothManager manager=activity.getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
   if(adapter==null){status.setText("Bu cihazda Bluetooth bulunamadı.");return;}if(!adapter.isEnabled()){status.setText("Bluetooth'u telefon ayarlarından aç.");return;}
   ArrayList<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());if(devices.isEmpty()){status.setText("Önce telefonun Bluetooth ayarlarında OBD adaptörünü eşleştir.");return;}
   String[] names=new String[devices.size()];for(int i=0;i<names.length;i++)names[i]=(devices.get(i).getName()==null?"Bluetooth cihazı":devices.get(i).getName())+"\n"+devices.get(i).getAddress();
   new AlertDialog.Builder(activity).setTitle("OBD adaptörünü seç").setItems(names,(d,index)->{invalidate();connection.connect(devices.get(index));}).setNegativeButton("Vazgeç",null).show();
  }catch(SecurityException e){status.setText("Bluetooth bağlantı izni gerekli.");}
 }
 private void invalidate(){timestamp=0;odometer=null;if(update!=null)update.setEnabled(false);if(stamp!=null)stamp.setText("Güncel ölçüm yok");for(ObdProtocol.Metric m:ObdProtocol.METRICS){TextView field=fields.get(m.pid);if(field!=null)field.setText(m.title+": —");}}
 @Override public void status(String s){if(!attached)return;status.setText(s);if(s.startsWith("Okuma kesildi")||s.startsWith("Bağlantı kurulamadı")){invalidate();codes.setText("Tarama tamamlanmadı.");}}
 @Override public void snapshot(Map<Integer,Double> values,Set<Integer> supported,Map<String,String> dtcs,long now){
  if(!attached)return;timestamp=now;odometer=values.get(0xA6);update.setEnabled(odometer!=null&&odometer>0&&odometer<2_000_000);
  stamp.setText("Son okuma: "+new SimpleDateFormat("dd.MM.yyyy HH:mm:ss",new Locale("tr","TR")).format(new Date(now)));
  for(ObdProtocol.Metric m:ObdProtocol.METRICS){Double v=values.get(m.pid);String result=!supported.contains(m.pid)?"Desteklenmiyor":v==null?"Yanıt alınamadı / uyuşmayan yanıt":String.format(Locale.US,"%.1f %s",v,m.unit);
   if(m.pid==1&&v!=null){int a=v.intValue();result=((a&128)!=0?"Motor lambası açık":"Motor lambası kapalı")+" · "+(a&127)+" kayıtlı kod";}
   fields.get(m.pid).setText(m.title+": "+result);
  }
  StringBuilder text=new StringBuilder();for(Map.Entry<String,String> e:dtcs.entrySet())text.append(e.getKey()).append("\n").append(e.getValue()).append("\n\n");codes.setText(text);
 }
 private void updateKm(){
  if(odometer==null||System.currentTimeMillis()-timestamp>120000){update.setEnabled(false);status.setText("Kilometre için önce güncel ölçüm al.");return;}
  AppDatabase db=new AppDatabase(activity);AppDatabase.Vehicle vehicle=db.getVehicle();int read=(int)Math.floor(odometer);
  if(read<vehicle.km){new AlertDialog.Builder(activity).setTitle("Kilometre uyuşmazlığı").setMessage("ECU: "+read+" km\nKayıtlı: "+vehicle.km+" km\nDüşük değer otomatik aktarılmaz. Araç göstergesini ve seçili aracı kontrol et.").setPositiveButton("Tamam",null).show();return;}
  new AlertDialog.Builder(activity).setTitle("Seçili aracın kilometresini güncelle?").setMessage(vehicle.brand+" "+vehicle.model+" · "+vehicle.plate+"\nMevcut: "+vehicle.km+" km\nECU toplam kilometre: "+read+" km\nAdaptörün bu araca bağlı olduğunu ve değerin göstergeyle uyumlu olduğunu kontrol et.")
   .setNegativeButton("Vazgeç",null).setPositiveButton("Onayla ve güncelle",(d,w)->{AppDatabase.Vehicle current=db.getVehicle();if(System.currentTimeMillis()-timestamp>120000||odometer==null||!current.plate.equals(vehicle.plate)||read<current.km){status.setText("Veri veya araç değişti. Yeniden ölçüm al.");return;}db.updateVehicle(current.brand,current.model,current.year,current.plate,read,current.fuelType);status.setText("Araç kilometresi güncellendi: "+read+" km");}).show();
 }
 public void pauseConnection(){connection.disconnect();invalidate();status.setText("Bağlantı duraklatıldı. Yeniden bağlanabilirsin.");}
 public void close(){attached=false;connection.close();}
 @Override protected void onDetachedFromWindow(){close();super.onDetachedFromWindow();}
}
