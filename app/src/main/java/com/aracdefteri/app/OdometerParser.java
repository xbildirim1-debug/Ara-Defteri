package com.aracdefteri.app;
import java.util.*;
import java.util.regex.*;
/** Conservative candidates only. No candidate is ever saved without confirmation. */
public final class OdometerParser {
    public static List<Integer> candidates(String text){
        LinkedHashSet<Integer> values=new LinkedHashSet<>();
        for(String line:text.split("\\r?\\n")){
            String lower=line.toLowerCase(Locale.ROOT);
            if(lower.contains("trip")||lower.contains("km/h")||lower.contains("kmh")||lower.contains("mph")||lower.contains("mile")||lower.contains("°")||lower.contains(":"))continue;
            Matcher m=Pattern.compile("(?<![0-9.,])(?:[0-9]{1,3}(?:[ .,'’][0-9]{3}){1,2}|[0-9]{4,7})(?![0-9.,])").matcher(line);
            while(m.find()){
                try{int value=Integer.parseInt(m.group().replaceAll("[^0-9]",""));if(value>=0&&value<=9999999)values.add(value);}catch(NumberFormatException ignored){}
            }
        }return new ArrayList<>(values);
    }
}
