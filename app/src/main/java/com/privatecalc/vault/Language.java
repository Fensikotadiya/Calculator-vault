package com.privatecalc.vault;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

/**
 * The languages the picker offers and the one choice it stores.
 * The first launch has nothing stored, so MainActivity shows the picker before the calculator.
 * Every launch after that reads the stored code here and applies it, whatever the device is set to.
 */
final class Language {
    /** One card in the picker: the stored code, the name in its own script, the English name and its flag. */
    static final class Option {
        final String code, own, english;
        final int flag;
        Option(String code, String own, String english, int flag) { this.code=code; this.own=own; this.english=english; this.flag=flag; }
    }
    /**
     * Every language the app is translated into. A card here must have a matching res/values-<code>
     * folder, or choosing it would hand the chooser back an English app.
     *
     * A language is not a country, so several cards share a flag: the six Indian languages all carry
     * India's, and the script on the card is what tells them apart. Where a language spans countries the
     * flag is the usual stand-in — Saudi Arabia for Arabic, Brazil for Portuguese (most of its speakers),
     * Pakistan for Urdu, Iran for Persian, Kenya for Swahili.
     *
     * The first six are the languages the app shipped with and keep their order; the rest follow roughly
     * by how many people speak them. The count is even, so the grid never needs its odd-row filler.
     */
    static final Option[] ALL = {
        new Option("en","English","English",R.drawable.flag_en),
        new Option("hi","हिन्दी","Hindi",R.drawable.flag_in),
        new Option("gu","ગુજરાતી","Gujarati",R.drawable.flag_in),
        new Option("es","Español","Spanish",R.drawable.flag_es),
        new Option("ar","العربية","Arabic",R.drawable.flag_sa),
        new Option("fr","Français","French",R.drawable.flag_fr),
        new Option("de","Deutsch","German",R.drawable.flag_de),
        new Option("pt","Português","Portuguese",R.drawable.flag_br),
        new Option("ru","Русский","Russian",R.drawable.flag_ru),
        new Option("bn","বাংলা","Bengali",R.drawable.flag_bd),
        new Option("ur","اردو","Urdu",R.drawable.flag_pk),
        // Indonesian is stored as "in", the obsolete code Java still reports for it and the one that
        // matches res/values-in. Writing "id" here would store a code no resource folder answers to.
        new Option("in","Bahasa Indonesia","Indonesian",R.drawable.flag_id),
        new Option("ja","日本語","Japanese",R.drawable.flag_jp),
        new Option("zh","中文","Chinese",R.drawable.flag_cn),
        new Option("ko","한국어","Korean",R.drawable.flag_kr),
        new Option("vi","Tiếng Việt","Vietnamese",R.drawable.flag_vn),
        new Option("tr","Türkçe","Turkish",R.drawable.flag_tr),
        new Option("it","Italiano","Italian",R.drawable.flag_it),
        new Option("nl","Nederlands","Dutch",R.drawable.flag_nl),
        new Option("pl","Polski","Polish",R.drawable.flag_pl),
        new Option("uk","Українська","Ukrainian",R.drawable.flag_ua),
        new Option("fa","فارسی","Persian",R.drawable.flag_ir),
        new Option("th","ไทย","Thai",R.drawable.flag_th),
        new Option("ms","Bahasa Melayu","Malay",R.drawable.flag_my),
        new Option("fil","Filipino","Filipino",R.drawable.flag_ph),
        new Option("sw","Kiswahili","Swahili",R.drawable.flag_ke),
        new Option("ta","தமிழ்","Tamil",R.drawable.flag_in),
        new Option("te","తెలుగు","Telugu",R.drawable.flag_in),
        new Option("mr","मराठी","Marathi",R.drawable.flag_in),
        new Option("pa","ਪੰਜਾਬੀ","Punjabi",R.drawable.flag_in),
        new Option("kn","ಕನ್ನಡ","Kannada",R.drawable.flag_in),
        new Option("ml","മലയാളം","Malayalam",R.drawable.flag_in),
    };
    // The same preferences file the PIN and the calculator settings live in.
    private static final String FILE = "access", KEY = "language";

    private Language() { }

    /** The stored code, or null while the picker has never been answered. */
    static String saved(Context context) {
        return context.getSharedPreferences(FILE,Context.MODE_PRIVATE).getString(KEY,null);
    }
    static boolean chosen(Context context) { return saved(context) != null; }
    /** Written with commit(), because the activity is recreated as soon as this returns. */
    static void save(Context context, String code) {
        context.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit().putString(KEY,code).commit();
    }
    /** Which card opens selected: the stored choice, else whatever the device already speaks, else English. */
    static int index(Context context) {
        String code = saved(context);
        if (code == null) code = Locale.getDefault().getLanguage();
        for (int i=0;i<ALL.length;i++) if (ALL[i].code.equals(code)) return i;
        return 0;
    }
    /**
     * Resources read through the returned context speak the stored language.
     * Until a language is stored this hands back the context untouched, so the picker itself
     * comes up in the device language and the first card is already the likely one.
     */
    static Context wrap(Context base) {
        String code = saved(base);
        if (code == null) return base;
        Locale locale = Locale.forLanguageTag(code);
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);   // Arabic lays the whole app out right to left
        return base.createConfigurationContext(configuration);
    }
}
