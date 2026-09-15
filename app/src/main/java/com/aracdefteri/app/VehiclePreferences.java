package com.aracdefteri.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

final class VehiclePreferences {
    static SharedPreferences open(Context context,int id) {
        SharedPreferences target=context.getSharedPreferences("vehicle_"+id,0);
        if(id==1&&!target.contains("migrated")) {
            SharedPreferences.Editor edit=target.edit();
            for(Map.Entry<String,?> e:context.getSharedPreferences("arac_defteri_prefs",0).getAll().entrySet())put(edit,e.getKey(),e.getValue());
            edit.putBoolean("migrated",true).commit();
        }
        return target;
    }
    static void put(SharedPreferences.Editor e,String key,Object v) {
        if(v instanceof String)e.putString(key,(String)v);else if(v instanceof Boolean)e.putBoolean(key,(Boolean)v);
        else if(v instanceof Integer)e.putInt(key,(Integer)v);else if(v instanceof Long)e.putLong(key,(Long)v);
        else if(v instanceof Float)e.putFloat(key,(Float)v);
    }
}
