package com.privatecalc.vault;

import java.util.*;

/** Tab separated note records with escaped line breaks, so a note of many lines is still one stored line. Kept free of Android types so the format can be checked on the JVM. */
final class Notes {
    /** One vault note. The stored line holds no raw tab or line break, so a note can never split or forge another. */
    static final class Entry {
        final String id, title, body; final long updated;
        private Entry(String id, String title, String body, long updated) { this.id=id; this.title=title; this.body=body; this.updated=updated; }
        /** What the list shows. A note saved without a title is headed by its first line instead. */
        String heading() {
            if (!title.isEmpty()) return shorten(title,48);
            String first = body.trim(); int stop = first.indexOf('\n');
            first = (stop < 0 ? first : first.substring(0,stop)).trim();
            return first.isEmpty() ? "Untitled note" : shorten(first,48);
        }
        /** The rest of the note flattened onto one line for the row under the heading. */
        String preview() {
            String rest = title.isEmpty() ? afterFirstLine(body) : body;
            StringBuilder flat = new StringBuilder(); boolean gap = true;
            for (int i=0;i<rest.length() && flat.length()<=90;i++) {
                char c = rest.charAt(i);
                if (c=='\n' || c=='\t' || c==' ') { if (!gap) { flat.append(' '); gap = true; } }
                else { flat.append(c); gap = false; }
            }
            String text = flat.toString().trim();
            return text.isEmpty() ? "No other text" : shorten(text,80);
        }
    }
    private static String shorten(String value, int limit) { return value.length() > limit ? value.substring(0,limit).trim() + "…" : value; }
    private static String afterFirstLine(String body) { int stop = body.trim().indexOf('\n'); return stop < 0 ? "" : body.trim().substring(stop+1); }
    /** The only way to build a note, so separators typed into the title cannot break the stored line. */
    static Entry entry(String id, String title, String body, long updated) {
        return new Entry(line(id),line(title),text(body),updated);
    }
    private static String line(String value) { return value == null ? "" : value.replace('\t',' ').replace('\r',' ').replace('\n',' ').trim(); }
    /** Bodies keep their line breaks; only the carriage returns a keyboard may add are normalised away. */
    private static String text(String value) { return value == null ? "" : value.replace("\r\n","\n").replace('\r','\n').trim(); }
    /** A note with neither a title nor a body is nothing to save. */
    static boolean blank(Entry entry) { return entry.title.isEmpty() && entry.body.isEmpty(); }
    static String encode(List<Entry> entries) {
        StringBuilder text = new StringBuilder();
        for (Entry entry : entries) text.append(entry.id).append('\t').append(entry.updated).append('\t').append(entry.title).append('\t').append(escape(entry.body)).append('\n');
        return text.toString();
    }
    /** Skips records an older or damaged write left unusable rather than losing every note. */
    static ArrayList<Entry> decode(String text) {
        ArrayList<Entry> entries = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\t",-1);
            if (parts.length < 4) continue;
            long updated; try { updated = Long.parseLong(parts[1].trim()); } catch (NumberFormatException e) { continue; }
            Entry entry = entry(parts[0],parts[2],unescape(parts[3]),updated);
            if (!entry.id.isEmpty() && !blank(entry)) entries.add(entry);
        }
        return entries;
    }
    /** Line breaks and tabs become two characters, so the record separators only ever appear between fields. */
    static String escape(String body) {
        StringBuilder out = new StringBuilder();
        for (char c : body.toCharArray()) {
            if (c=='\\') out.append("\\\\"); else if (c=='\n') out.append("\\n"); else if (c=='\t') out.append("\\t"); else if (c=='\r') out.append(' '); else out.append(c);
        }
        return out.toString();
    }
    static String unescape(String stored) {
        StringBuilder out = new StringBuilder();
        for (int i=0;i<stored.length();i++) {
            char c = stored.charAt(i);
            if (c != '\\' || i+1 == stored.length()) { out.append(c); continue; }
            char next = stored.charAt(++i);
            out.append(next=='n' ? '\n' : next=='t' ? '\t' : next);
        }
        return out.toString();
    }
}
