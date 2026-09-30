package com.privatecalc.vault;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/** Turns what is typed in the address bar into an address to load. Kept free of Android types so it can be checked on the JVM. */
final class Browser {
    static final String HOME = "https://www.google.com/";
    private static final String SEARCH = "https://www.google.com/search?q=";
    /** A bare host, optionally with a port and a path. Anything else is treated as words to search for. */
    private static final String HOST = "[a-zA-Z0-9][a-zA-Z0-9-]*([.][a-zA-Z0-9][a-zA-Z0-9-]*)*[.][a-zA-Z]{2,}(:[0-9]{1,5})?([/?#].*)?";

    /**
     * The address a typed line should open.
     * Only https is ever returned: the app blocks cleartext, so http is upgraded, and every other
     * scheme (javascript, file, data, intent, tel) becomes a search rather than something to load.
     */
    static String target(String typed) {
        String value = typed == null ? "" : typed.trim();
        if (value.isEmpty()) return HOME;
        String lower = value.toLowerCase(java.util.Locale.US);
        if (lower.startsWith("https://")) return value;
        if (lower.startsWith("http://")) return "https://" + value.substring(7);
        if (!value.contains(" ") && value.matches(HOST)) return "https://" + value;
        return SEARCH + encode(value);
    }
    private static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); } catch (UnsupportedEncodingException e) { return ""; }
    }
    /**
     * The site name to show beside the address.
     * Anything before an "@" is dropped, so https://bank.example@attacker.test/ reads as attacker.test.
     */
    static String host(String url) {
        String value = url == null ? "" : url.trim();
        int scheme = value.indexOf("://");
        if (scheme >= 0) value = value.substring(scheme + 3);
        int end = value.length();
        for (int i = 0; i < value.length(); i++) { char c = value.charAt(i); if (c == '/' || c == '?' || c == '#') { end = i; break; } }
        value = value.substring(0, end);
        int at = value.lastIndexOf('@');
        if (at >= 0) value = value.substring(at + 1);
        int port = value.indexOf(':');
        if (port >= 0) value = value.substring(0, port);
        if (value.toLowerCase(java.util.Locale.US).startsWith("www.")) value = value.substring(4);
        return value;
    }
    /** Blank pages the browser loads itself; they are never shown as an address. */
    static boolean blank(String url) { return url == null || url.isEmpty() || url.equals("about:blank"); }
}
