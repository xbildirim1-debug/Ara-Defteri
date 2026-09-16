package com.aracdefteri.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** OCR words retain positions: fields are associated with labels and columns. */
public final class DocumentLayout {
    public static final class Word {
        public final String text;
        public final float left, top, right, bottom;
        public Word(String text,float l,float t,float r,float b){this.text=text;left=l;top=t;right=r;bottom=b;}
        float cy(){return (top+bottom)/2;}
        float height(){return Math.max(1,bottom-top);}
    }
    public final List<Word> words=new ArrayList<>();
    public void add(String text,float l,float t,float r,float b){words.add(new Word(text,l,t,r,b));}
    public static String norm(String s){return s==null?"":s.toLowerCase(new Locale("tr","TR")).replace('ı','i').replace('ş','s').replace('ğ','g').replace('ü','u').replace('ö','o').replace('ç','c');}
    public String text(){
        List<Word> sorted=new ArrayList<>(words);sorted.sort(Comparator.comparingDouble(w->w.top));
        List<List<Word>> rows=new ArrayList<>();
        for(Word w:sorted){
            List<Word> row=null;
            for(int i=rows.size()-1;i>=Math.max(0,rows.size()-4);i--){Word a=rows.get(i).get(0);if(Math.abs(w.cy()-a.cy())<Math.max(w.height(),a.height())*.55f){row=rows.get(i);break;}}
            if(row==null){row=new ArrayList<>();rows.add(row);}row.add(w);
        }
        StringBuilder out=new StringBuilder();
        for(List<Word> row:rows){row.sort(Comparator.comparingDouble(w->w.left));for(Word w:row){if(out.length()>0&&out.charAt(out.length()-1)!='\n')out.append(' ');out.append(w.text);}out.append('\n');}
        return out.toString();
    }
    public List<Word> odometerLabels(){List<Word> out=new ArrayList<>();for(Word w:words)if(norm(w.text).replaceAll("[^a-z]","").matches("odo|odometer|odometre"))out.add(w);return out;}
    public int odometer(){
        int result=0;
        for(Word label:odometerLabels()){
            List<Word> near=new ArrayList<>();
            for(Word w:words){if(w==label)continue;boolean same=Math.abs(w.cy()-label.cy())<Math.max(w.height(),label.height())*.75f;if(same&&w.left>=label.right-label.height()*.3f&&w.left-label.right<16*label.height())near.add(w);}
            near.sort(Comparator.comparingDouble(w->w.left));StringBuilder number=new StringBuilder();float edge=label.right;
            for(Word w:near){if(!w.text.matches("[0-9]+(?:[.,][0-9]{3})*"))break;if(number.length()>0&&w.left-edge>Math.max(label.height(),w.height())*.9f)break;number.append(w.text);edge=w.right;}
            int value=wholeKm(number.toString());if(value>0){if(result>0&&result!=value)return 0;result=value;}
        }
        return result;
    }
    public static int wholeKm(String s){String v=s.trim();if(!v.matches("(?:[0-9]{3,7}|[0-9]{1,3}(?:[., ][0-9]{3}){1,2})"))return 0;try{int n=Integer.parseInt(v.replaceAll("[^0-9]",""));return n>0&&n<2_000_000?n:0;}catch(Exception e){return 0;}}
    public void apply(RecordParser.Parsed p){
        if("ODOMETER".equals(p.documentKind)||(p.type.isEmpty()&&!odometerLabels().isEmpty())){p.type="";p.documentKind="ODOMETER";p.km=odometer();}
        if(OilCardParser.isCard(text()))OilCardParser.apply(text(),p);
    }
}
