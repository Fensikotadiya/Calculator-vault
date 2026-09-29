package com.privatecalc.vault;

import java.util.*;

public class NotesTest {
    private static int checks = 0;
    public static void main(String[] args) {
        // Fields survive a roundtrip unchanged, including a body of several lines and a non-Latin note.
        ArrayList<Notes.Entry> saved = new ArrayList<>();
        saved.add(Notes.entry("n-1","Shopping","milk\nbread\tbutter",1000L));
        saved.add(Notes.entry("n-2","","પહેલી લીટી\nબીજી લીટી",2000L));
        saved.add(Notes.entry("n-3","Wifi","",3000L));
        ArrayList<Notes.Entry> read = Notes.decode(Notes.encode(saved));
        same(3,read.size(),"All records read back");
        for (int i=0;i<saved.size();i++) {
            same(saved.get(i).id,read.get(i).id,"Identifier kept");
            same(saved.get(i).title,read.get(i).title,"Title kept");
            same(saved.get(i).body,read.get(i).body,"Body kept");
            same(saved.get(i).updated,read.get(i).updated,"Edit time kept");
        }
        same("milk\nbread\tbutter",read.get(0).body,"Line breaks and tabs inside a body survive");

        // The list rows read from the title when there is one and from the body when there is not.
        same("Shopping",read.get(0).heading(),"Titled note heads with its title");
        same("પહેલી લીટી",read.get(1).heading(),"Untitled note heads with its first line");
        same("બીજી લીટી",read.get(1).preview(),"Untitled note previews the rest of the body");
        same("milk bread butter",read.get(0).preview(),"Titled note previews the whole body flattened");
        same("No other text",read.get(2).preview(),"Note with no body says so");
        same("Untitled note",Notes.entry("n-4","","   ",4000L).heading(),"Nothing to head with falls back");
        String wordy = Notes.entry("n-5","AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","x",5000L).heading();
        same(49,wordy.length(),"Long heading is shortened");
        same(true,wordy.endsWith("…"),"Shortened heading is marked");

        // Separators typed into a title cannot split or forge a record; a body keeps its shape instead.
        Notes.Entry hostile = Notes.entry("n-6","Tab\there\nand break","line\tone\nline two",6000L);
        same("Tab here and break",hostile.title,"Separators replaced in the title");
        ArrayList<Notes.Entry> parsed = Notes.decode(Notes.encode(Collections.singletonList(hostile)));
        same(1,parsed.size(),"Hostile input is still one record");
        same("line\tone\nline two",parsed.get(0).body,"Body survives as one field");

        // A backslash the user typed is stored escaped and comes back as the backslash, not as a line break.
        Notes.Entry literal = Notes.entry("n-7","Path","a\\nb and C:\\temp",7000L);
        same("a\\nb and C:\\temp",Notes.decode(Notes.encode(Collections.singletonList(literal))).get(0).body,"Typed backslashes are not read as escapes");
        same("one\\\\two",Notes.escape("one\\two"),"A backslash is stored doubled");
        same("a\\nb",Notes.escape("a\nb"),"A line break is stored as two characters");
        same("a\\tb",Notes.escape("a\tb"),"A tab is stored as two characters");
        same("a\nb",Notes.unescape("a\\nb"),"Two characters read back as a line break");
        same("trailing\\",Notes.unescape("trailing\\"),"A backslash at the end is kept as itself");

        // Carriage returns a keyboard may add are normalised, and surrounding blank space is dropped.
        same("a\nb",Notes.entry("n-8","","a\r\nb",8000L).body,"Windows line breaks normalised");
        same("a\nb",Notes.entry("n-9","","a\rb",9000L).body,"Lone carriage return normalised");
        same("spaced",Notes.entry("n-10","  spaced  ","x",10000L).title,"Surrounding spaces trimmed from the title");
        same("hello",Notes.entry("n-11","t","  hello  ",11000L).body,"Surrounding spaces trimmed from the body");

        // A note with neither a title nor a body is nothing to save.
        same(true,Notes.blank(Notes.entry("n-12","","",12000L)),"Empty note is blank");
        same(false,Notes.blank(Notes.entry("n-13","","text",13000L)),"Body alone is not blank");
        same(false,Notes.blank(Notes.entry("n-14","title","",14000L)),"Title alone is not blank");

        // Unusable records are skipped instead of losing the rest of the notes.
        ArrayList<Notes.Entry> partial = Notes.decode(
            "n-a\t100\tKeep\tbody\nbroken\n\t200\tNo id\tbody\nn-c\tnotanumber\tTitle\tbody\nn-d\t300\t\t\nn-e\t400\tGood\t");
        same(2,partial.size(),"Damaged records dropped, good ones kept");
        same("Keep",partial.get(0).title,"First good record kept");
        same(100L,partial.get(0).updated,"Edit time parsed");
        same("Good",partial.get(1).title,"Record with an empty body kept");
        same(0,Notes.decode("").size(),"Empty file is an empty list");
        same("",Notes.encode(new ArrayList<>()),"Empty list encodes to nothing");
        System.out.println("Notes: " + checks + " record format and listing checks passed");
    }
    private static void same(Object expected, Object actual, String description) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(description + ": expected <" + expected + "> but was <" + actual + ">");
    }
}
