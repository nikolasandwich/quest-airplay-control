package com.localair.airplay;

import android.content.Context;
import android.content.res.Resources;
import java.util.Locale;

/** Android resources in production, generated English defaults for plain JVM models. */
public final class AppText {
    private static volatile Resources resources;
    private AppText() {}
    public static void initialize(Context context){resources=context.getApplicationContext().getResources();}
    public static String get(int id,Object... args){
        Resources current=resources;
        String pattern=current==null?DefaultText.get(id):current.getString(id);
        Locale locale=current==null?Locale.ENGLISH:current.getConfiguration().getLocales().get(0);
        return args.length==0?pattern:String.format(locale,pattern,args);
    }
    public static String language(){
        Resources current=resources;
        if(current==null)return "en";
        return current.getString(R.string.ui_language);
    }
    static void resetForTests(){resources=null;}
}
