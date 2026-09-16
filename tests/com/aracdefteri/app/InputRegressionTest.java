package com.aracdefteri.app;
import javax.imageio.ImageIO;
import java.io.File;
import java.awt.image.BufferedImage;
import java.util.*;
public class InputRegressionTest {
 static int checks=0;
 static void eq(Object a,Object b){checks++;if(!Objects.equals(a,b))throw new AssertionError("expected="+b+" actual="+a);}
 public static void main(String[] args)throws Exception{
  var p=RecordParser.fromVoice("10 litre yakıt aldım 873 lira kilometre 321609");eq(p.type,"Yakıt");eq(p.km,321609);eq(p.quantity,10.0);eq(p.amount,873.0);
  p=RecordParser.fromVoice("on litre benzin aldım sekiz yüz yetmiş üç lira kilometre de iki yüz otuz beş bin dört yüz seksen yedi");eq(p.type,"Yakıt");eq(p.km,235487);eq(p.quantity,10.0);eq(p.amount,873.0);
  p=RecordParser.fromVoice("Kilometrem 321.609 oldu. 10 litre yakıt aldım. 873 lira");eq(p.type,"Yakıt");eq(p.km,321609);
  p=RecordParser.fromText("Doğa Sigorta\nZORUNLU MALİ SORUMLULUK TRAFİK SİGORTA POLİÇESİ\nBaşlama Tarihi: 24.09.2020 Bitiş Tarihi: 24.09.2021");eq(p.vendor,"Doğa Sigorta");eq(p.policyEndDate,"24.09.2021");eq(p.policyStartDate,"24.09.2020");
  p=RecordParser.fromText("Yağ değişim kartı\nDeğiştiği Km Değişeceği Km\nMotor yağı\nYağ filtresi");eq(p.maintenanceParts.size(),0);eq(p.km,0);eq(p.nextKm,0);
  p=RecordParser.fromText("Yağ değişim kartı\nDeğiştiği Km Değişeceği Km\nMotor yağı 235.487 245.487\nYağ filtresi 235487 245487\nHava filtresi");eq(p.maintenanceParts.size(),2);eq(p.km,235487);eq(p.nextKm,245487);
  p=RecordParser.fromText("41 MEHMETÇİK PETROL\n11-02-2024\n15,520 LT X 41,90\nEXCELLIUM EURODI %20 *650,29\nTOPKDV *108,38\nTOPLAM *650,29");eq(p.type,"Yakıt");eq(p.quantity,15.52);eq(p.amount,650.29);
  DocumentLayout d=new DocumentLayout();d.add("100",0,0,30,20);d.add("120",35,0,65,20);d.add("ODO",0,60,30,75);d.add("321609",40,56,120,78);eq(d.odometer(),321609);
  DocumentLayout e=new DocumentLayout();e.add("100",0,0,30,20);e.add("120",35,0,65,20);eq(e.odometer(),0);
  if(args.length>0){BufferedImage image=ImageIO.read(new File(args[0]));int[] pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());var r=BodyDiagramReader.read(image.getWidth(),image.getHeight(),pixels);System.out.println(r);eq(r.get("Kaput"),"Değişen");eq(r.get("Sağ ön kapı"),"Boyalı");eq(r.get("Sağ arka kapı"),"Boyalı");eq(r.get("Sol arka çamurluk"),"Lokal boyalı");eq(r.get("Sağ arka çamurluk"),"Lokal boyalı");}
  System.out.println("PASS "+checks+" checks");
 }
}
