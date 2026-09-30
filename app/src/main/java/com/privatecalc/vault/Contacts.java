package com.privatecalc.vault;

import java.util.*;

/** Tab separated contact records. Kept free of Android types so the format can be checked on the JVM. */
final class Contacts {
    /** One vault contact. Fields never contain a tab or a line break, so a record is always one line. */
    static final class Entry {
        final String id, name, number, note;
        private Entry(String id, String name, String number, String note) { this.id=id; this.name=name; this.number=number; this.note=note; }
        String title() { return name.isEmpty() ? number : name; }
    }
    /** The only way to build an entry, so separators typed into a field cannot break the stored line. */
    static Entry entry(String id, String name, String number, String note) {
        return new Entry(clean(id),clean(name),clean(number),clean(note));
    }
    private static String clean(String value) { return value == null ? "" : value.replace('\t',' ').replace('\r',' ').replace('\n',' ').trim(); }
    /** Keeps only what a tel: URI can carry, so a pasted number with spaces or brackets still dials. */
    static String dialable(String number) {
        StringBuilder digits = new StringBuilder(); boolean any = false;
        for (char c : clean(number).toCharArray()) {
            if (c >= '0' && c <= '9') { any = true; digits.append(c); }
            else if (c == '+' || c == '*' || c == '#' || c == ',' || c == ';') digits.append(c);
        }
        return any ? digits.toString() : "";
    }
    static String encode(List<Entry> entries) {
        StringBuilder text = new StringBuilder();
        for (Entry entry : entries) text.append(entry.id).append('\t').append(entry.name).append('\t').append(entry.number).append('\t').append(entry.note).append('\n');
        return text.toString();
    }
    /** Skips records an older or damaged write left unusable rather than losing the whole list. */
    static ArrayList<Entry> decode(String text) {
        ArrayList<Entry> entries = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\t",-1);
            if (parts.length < 3) continue;
            Entry entry = entry(parts[0],parts[1],parts[2],parts.length > 3 ? parts[3] : "");
            if (!entry.id.isEmpty() && !dialable(entry.number).isEmpty()) entries.add(entry);
        }
        return entries;
    }
}
