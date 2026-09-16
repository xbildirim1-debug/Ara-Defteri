package com.aracdefteri.app;
import java.util.HashMap;
import java.util.Map;
/** Convert contiguous Turkish number phrases; unrelated amounts are never added together. */
public final class VoiceNumbers {
 private static final Map<String,Integer> N=new HashMap<>();
 static {String[] ones={"sifir","bir","iki","uc","dort","bes","alti","yedi","sekiz","dokuz"};for(int i=0;i<ones.length;i++)N.put(ones[i],i);String[] tens={"on","yirmi","otuz","kirk","elli","altmis","yetmis","seksen","doksan"};for(int i=0;i<tens.length;i++)N.put(tens[i],(i+1)*10);}
 public static String normalize(String input){
  if(input==null)return "";
  String s=DocumentLayout.norm(input).replaceAll("(?<=\\d)[.,](?=\\d{3}(?:\\D|$))", "");
  String[] words=s.replaceAll("([,;!?])", " $1 ").split("\\s+");
  StringBuilder out=new StringBuilder();long total=0,group=0;boolean number=false;
  for(String w:words){
   if(N.containsKey(w)){group+=N.get(w);number=true;}
   else if(w.matches("[0-9]+")){group+=Long.parseLong(w.length()>8?"0":w);number=true;}
   else if(w.equals("yuz")){group=Math.max(1,group)*100;number=true;}
   else if(w.equals("bin")||w.equals("milyon")){total+=Math.max(1,group)*(w.equals("bin")?1000:1000000);group=0;number=true;}
   else {if(number){out.append(total+group).append(' ');total=group=0;number=false;}out.append(w).append(' ');}
  }
  if(number)out.append(total+group);
  return out.toString().replaceAll("kilometre(?:m)?\\s+(?:de|da)\\s+", "kilometre ").replaceAll("kilometrem\\s+", "kilometre ");
 }
}
