package com.aracdefteri.app;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** One read-only command at a time; cancellation closes the socket and invalidates callbacks. */
public final class ObdBluetooth {
 public interface Listener {void status(String status);void snapshot(Map<Integer,Double> values,Set<Integer> supported,Map<String,String> codes,long capturedAt);}
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
 private volatile BluetoothSocket socket;private volatile int generation;private volatile boolean ready,busy,closed;
 private final Listener listener;private final Set<Integer> supported=new LinkedHashSet<>();
 public ObdBluetooth(Listener listener){this.listener=listener;}
 private void status(int id,String s){main.post(()->{if(id==generation&&!closed)listener.status(s);});}
 public void connect(BluetoothDevice device){
  disconnect();if(closed)return;int id=generation;busy=true;status(id,"Adaptöre bağlanılıyor…");
  worker.execute(()->{
   try{
    BluetoothSocket active=device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"));
    if(id!=generation){active.close();return;}socket=active;
    ScheduledFuture<?> timeout=timer.schedule(()->{try{active.close();}catch(IOException ignored){}},15,TimeUnit.SECONDS);
    try{active.connect();}finally{timeout.cancel(false);}
    for(String cmd:new String[]{"ATZ","ATE0","ATL0","ATS1","ATH0","ATSP0"}){
     String response=command(cmd,id);if(response.contains("?")||response.contains("ERROR"))throw new IOException("Adaptör komutu desteklemiyor: "+cmd);
    }
    supported.clear();
    for(int base=0;base<=0xA0;base+=32){String raw=command(String.format(Locale.US,"01%02X",base),id);Set<Integer> page=ObdProtocol.supported(raw,base);
     if(base==0&&ObdProtocol.payloads(raw,0x41).isEmpty())throw new IOException("ECU yanıt vermedi. Kontağı ve adaptör uyumluluğunu kontrol et.");
     supported.addAll(page);if(!page.contains(base+32))break;
    }
    if(id!=generation)return;ready=true;busy=false;status(id,"Bağlandı. "+supported.size()+" standart veri desteği bildirildi.");refresh();
   }catch(Exception e){if(id==generation){ready=false;busy=false;closeSocket();status(id,"Bağlantı kurulamadı: "+e.getMessage());}}
  });
 }
 public void refresh(){if(!ready||busy||closed)return;busy=true;int id=generation;status(id,"Araç verileri ve arıza kodları okunuyor…");
  worker.execute(()->{
   try{
    Map<Integer,Double> values=new LinkedHashMap<>();Map<String,String> codes=new LinkedHashMap<>();
    for(ObdProtocol.Metric m:ObdProtocol.METRICS){if(!supported.contains(m.pid))continue;String raw=command(String.format(Locale.US,"01%02X",m.pid),id);Double v=ObdProtocol.value(raw,m);if(v!=null)values.put(m.pid,v);}
    int[] services={3,7,10};String[] labels={"Kayıtlı","Bekleyen","Kalıcı"};
    for(int i=0;i<services.length;i++){
     String raw=command(String.format(Locale.US,"%02X",services[i]),id);List<byte[]> data=ObdProtocol.payloads(raw,services[i]+64);
     if(data.isEmpty()){codes.put(labels[i]+" taraması", "Yanıt alınamadı / desteklenmiyor");continue;}
     Set<String> dtcs=ObdProtocol.dtcs(raw,services[i]+64);
     if(dtcs.isEmpty())codes.put(labels[i]+" taraması","Kod bildirilmedi. Bu sonuç tüm kontrol ünitelerini kapsamaz.");
     for(String code:dtcs)codes.put(code+" · "+labels[i],ObdProtocol.describe(code));
    }
    long now=System.currentTimeMillis();Set<Integer> capabilities=new LinkedHashSet<>(supported);
    main.post(()->{if(id==generation&&!closed)listener.snapshot(values,capabilities,codes,now);});status(id,"Okuma tamamlandı. Ölçümler anlık görüntüdür; yenilemek için düğmeye bas.");
   }catch(Exception e){if(id==generation){ready=false;closeSocket();status(id,"Okuma kesildi: "+e.getMessage()+". Önceki veriler güncel değildir.");}}
   finally{if(id==generation)busy=false;}
  });
 }
 private String command(String cmd,int id)throws Exception{
  if(!ObdProtocol.allowed(cmd))throw new IOException("İzin verilmeyen yazma komutu");
  BluetoothSocket active=socket;if(id!=generation||active==null)throw new IOException("Bağlantı kapalı");
  OutputStream out=active.getOutputStream();InputStream in=active.getInputStream();out.write((cmd+"\r").getBytes(StandardCharsets.US_ASCII));out.flush();
  StringBuilder b=new StringBuilder();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(cmd.equals("0100")?15:6);
  while(System.nanoTime()<end){
   if(id!=generation||socket!=active)throw new IOException("İptal edildi");
   if(in.available()>0){int value=in.read();if(value<0)throw new EOFException("Adaptör bağlantısı kapandı");if(value=='>'){Thread.sleep(100);return b.toString().toUpperCase(Locale.ROOT);}b.append((char)value);if(b.length()>16000)throw new IOException("Adaptör yanıtı çok uzun");}
   else Thread.sleep(20);
  }
  throw new IOException("Yanıt zaman aşımı");
 }
 private void closeSocket(){BluetoothSocket s=socket;socket=null;if(s!=null)try{s.close();}catch(IOException ignored){}}
 public void disconnect(){generation++;ready=false;busy=false;closeSocket();}
 public void close(){closed=true;disconnect();worker.shutdownNow();timer.shutdownNow();}
}
