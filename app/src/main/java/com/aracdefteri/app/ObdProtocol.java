package com.aracdefteri.app;
import java.util.*;
/** Standard, read-only SAE services. Unsupported/malformed data is never converted to zero. */
public final class ObdProtocol {
 public static final class Metric {
  public final int pid,bytes;public final String title,unit;
  Metric(int pid,int bytes,String title,String unit){this.pid=pid;this.bytes=bytes;this.title=title;this.unit=unit;}
 }
 public static final Metric[] METRICS={
  new Metric(0x01,4,"Motor lambası / kayıtlı kod sayısı",""),new Metric(0x04,1,"Hesaplanan motor yükü","%"),
  new Metric(0x05,1,"Soğutma suyu sıcaklığı","°C"),new Metric(0x06,1,"Kısa süreli yakıt düzeltmesi B1","%"),new Metric(0x07,1,"Uzun süreli yakıt düzeltmesi B1","%"),
  new Metric(0x0B,1,"Emme manifoldu mutlak basıncı","kPa"),new Metric(0x0C,2,"Motor devri","dev/dk"),new Metric(0x0D,1,"Araç hızı","km/sa"),
  new Metric(0x0E,1,"Ateşleme avansı","°"),new Metric(0x0F,1,"Emme havası sıcaklığı","°C"),new Metric(0x10,2,"Hava kütle akışı","g/sn"),new Metric(0x11,1,"Gaz kelebeği konumu","%"),
  new Metric(0x1F,2,"Motorun çalıştığı süre","sn"),new Metric(0x21,2,"Motor lambası açıkken gidilen mesafe","km"),new Metric(0x23,2,"Yakıt rayı basıncı","kPa"),
  new Metric(0x2C,1,"İstenen EGR","%"),new Metric(0x2F,1,"Yakıt seviyesi","%"),new Metric(0x31,2,"Kodlar silindikten sonraki mesafe","km"),
  new Metric(0x33,1,"Atmosfer basıncı","kPa"),new Metric(0x42,2,"Kontrol ünitesi voltajı","V"),new Metric(0x46,1,"Dış hava sıcaklığı","°C"),
  new Metric(0x5C,1,"Motor yağı sıcaklığı","°C"),new Metric(0x5E,2,"Yakıt tüketim debisi","L/sa"),new Metric(0xA6,4,"ECU toplam kilometre","km")};
 public static List<byte[]> payloads(String raw,int service){
  List<byte[]> out=new ArrayList<>();String prefix=String.format(Locale.US,"%02X",service);
  if(raw==null)return out;
  StringBuilder frames=null;int expected=0,sequence=0;
  for(String row:raw.toUpperCase(Locale.ROOT).split("[\\r\\n>]+")){
   String line=row.trim();if(line.isEmpty())continue;
   if(line.matches("[0-9A-F]{3}")){expected=Integer.parseInt(line,16);frames=new StringBuilder();sequence=0;continue;}
   if(line.matches("[0-9A-F]:.*")){
    int index=Integer.parseInt(line.substring(0,1),16);String data=line.substring(2).replaceAll("\\s", "");
    if(frames==null||index!=sequence++||!data.matches("(?:[0-9A-F]{2})+")){frames=null;continue;}
    frames.append(data);
    if(frames.length()>=expected*2){line=frames.substring(0,expected*2);frames=null;}else continue;
   }
   String hex=line.replaceAll("\\s", "");
   if(!hex.matches("(?:[0-9A-F]{2})+")||!hex.startsWith(prefix))continue;
   byte[] bytes=new byte[hex.length()/2];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);out.add(bytes);
  }
  return out;
 }
 public static Set<Integer> supported(String raw,int base){
  Set<Integer> out=new LinkedHashSet<>();for(byte[] p:payloads(raw,0x41)){if(p.length<6||(p[1]&255)!=base)continue;
   for(int bit=0;bit<32;bit++)if(((p[2+bit/8]&255)&(1<<(7-bit%8)))!=0)out.add(base+bit+1);
  }return out;
 }
 public static Double value(String raw,Metric metric){
  Double result=null;
  for(byte[] p:payloads(raw,0x41)){if(p.length<2+metric.bytes||(p[1]&255)!=metric.pid)continue;int a=p[2]&255,b=metric.bytes>1?p[3]&255:0;double v;
   switch(metric.pid){
    case 0x01:v=a;break;
    case 0x04:case 0x11:case 0x2C:case 0x2F:v=a*100.0/255;break;
    case 0x05:case 0x0F:case 0x46:case 0x5C:v=a-40;break;
    case 0x06:case 0x07:v=(a-128)*100.0/128;break;
    case 0x0C:v=(256*a+b)/4.0;break;
    case 0x0E:v=a/2.0-64;break;
    case 0x10:v=(256*a+b)/100.0;break;
    case 0x1F:case 0x21:case 0x31:v=256*a+b;break;
    case 0x23:v=(256*a+b)*10.0;break;
    case 0x42:v=(256*a+b)/1000.0;break;
    case 0x5E:v=(256*a+b)/20.0;break;
    case 0xA6:v=((long)a*16777216L+(long)b*65536L+(p[4]&255)*256L+(p[5]&255))/10.0;break;
    default:v=a;
   }
   if(result!=null&&Math.abs(result-v)>.01)return null;result=v;
  }return result;
 }
 public static boolean validDtcResponse(String raw,int service){
  List<byte[]> frames=payloads(raw,service);if(frames.isEmpty())return false;
  for(byte[] p:frames){if(p.length<2)return false;
   if((p.length-1)%2!=0 && p.length!=2+(p[1]&255)*2)return false;
  }return true;
 }
 public static boolean validSupportResponse(String raw,int base){
  for(byte[] p:payloads(raw,0x41))if(p.length>=6&&(p[1]&255)==base)return true;return false;
 }
 public static Set<String> dtcs(String raw,int service){
  Set<String> out=new LinkedHashSet<>();for(byte[] p:payloads(raw,service)){
   int start=1;
   // CAN responses may prefix the DTC count. Use parity plus exact count to avoid shifting DTC bytes.
   if((p.length-1)%2!=0){int count=p.length>1?p[1]&255:0;if(p.length!=2+count*2)continue;start=2;}
   for(int i=start;i+1<p.length;i+=2){int a=p[i]&255,b=p[i+1]&255;if((a|b)==0)continue;out.add("PCBU".charAt(a>>6)+String.format(Locale.US,"%01X%01X%02X",(a>>4)&3,a&15,b));}
  }return out;
 }
 public static boolean allowed(String command){return command.matches("AT(?:Z|E0|L0|S1|H0|SP0|DP|DPN|RV)")||command.matches("01[0-9A-F]{2}")||command.equals("03")||command.equals("07")||command.equals("0A");}
 public static String describe(String code){
  switch(code){
   case "P0300":return "Rastgele/birden fazla silindirde tekleme tespit edildi. Ateşleme, yakıt beslemesi, hava kaçağı veya kompresyon kontrolü gerekebilir. Kod tek başına arızalı parçayı belirlemez.";
   case "P0171":return "Karışım fakir (silindir grubu 1). Emme kaçağı, hava ölçümü ve yakıt beslemesi birlikte kontrol edilir.";
   case "P0172":return "Karışım zengin (silindir grubu 1). Yakıt basıncı, enjektörler ve hava/yakıt sensörleri ölçümle kontrol edilir.";
   case "P0420":return "Katalizör verimi eşik altında (silindir grubu 1). Egzoz kaçağı, sensörler ve motorun yanma durumu incelenmeden katalizör arızası kesinleştirilemez.";
   case "P0430":return "Katalizör verimi eşik altında (silindir grubu 2). Egzoz ve sensör kontrolleri gerekir.";
   case "P0401":return "EGR akışı yetersiz. Tıkanıklık, valf, kumanda ve sensörlerin kontrolü gerekir.";
   case "P0299":return "Turbo/süperşarj basıncı beklenenin altında. Hortum-kaçak, kumanda ve turbo sistemi kontrol edilir.";
   case "P0118":return "Soğutma suyu sıcaklık sensörü devresinde yüksek sinyal. Kablo/soket ve sensör ölçümü gerekir; bu kod tek başına hararet demek değildir.";
   case "P0128":return "Soğutma suyu sıcaklığı termostat düzenleme sıcaklığının altında. Termostat ve sıcaklık ölçümü kontrol edilir.";
   case "P0520":return "Motor yağ basıncı sensörü/anahtarı devresi. Gerçek basınç ile sensör/kablo arızası ayrılmalıdır.";
   case "P0521":return "Motor yağ basıncı sensörü/anahtarı aralık-performans sorunu. Gerçek yağ basıncı ölçülmeden yalnız sensör değişimi önerilmez.";
   case "P0522":return "Motor yağ basıncı sensörü devresinde düşük sinyal. Gerçek basınç düşüklüğü ile elektriksel sorun ayrılmalıdır.";
   case "P0523":return "Motor yağ basıncı sensörü devresinde yüksek sinyal. Sensör, soket ve kablo kontrolü gerekir.";
   case "P0524":return "Motor yağ basıncı çok düşük olarak raporlandı. Motoru güvenli yerde durdur; servis kontrolü gerekir.";
   default:
    if(code.matches("P030[1-8]"))return code.charAt(4)+". silindirde tekleme tespit edildi. Buji/bobin, enjektör ve kompresyon kontrolleri gerekebilir.";
    return "Açıklama doğrulanamadı. Marka, model, yıl, motor ve kodu veren kontrol ünitesiyle üretici servis kaynağından kontrol edilmelidir.";
  }
 }
}
