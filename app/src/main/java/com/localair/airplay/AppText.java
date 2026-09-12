package com.localair.airplay;

import android.content.Context;
import android.content.res.Resources;
import java.util.Locale;

/** Android resources in production, generated English defaults for plain JVM models. */
public final class AppText {
    private static volatile Resources resources;
    private AppText() {}
    public static String selection(Context context){
        String tag=context.getSharedPreferences("app_language",Context.MODE_PRIVATE).getString("selection","");
        return supported(tag)?tag:"";
    }
    private static boolean supported(String tag){return "".equals(tag)||"en".equals(tag)||"de".equals(tag)||"fr".equals(tag)||"zh-CN".equals(tag);}
    public static void initialize(Context context){
        Context app=context.getApplicationContext();
        String tag=selection(app);
        if(tag.isEmpty()){resources=app.getResources();return;}
        android.content.res.Configuration config=new android.content.res.Configuration(app.getResources().getConfiguration());
        config.setLocales(new android.os.LocaleList(Locale.forLanguageTag(tag)));
        resources=app.createConfigurationContext(config).getResources();
    }
    public static void setLanguage(Context context,String tag){
        if(!supported(tag))throw new IllegalArgumentException("Unsupported app language");
        context.getSharedPreferences("app_language",Context.MODE_PRIVATE).edit().putString("selection",tag).apply();
        initialize(context);
    }
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
