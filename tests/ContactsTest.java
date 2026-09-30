package com.privatecalc.vault;

import java.util.*;

public class ContactsTest {
    private static int checks = 0;
    public static void main(String[] args) {
        // Fields survive a roundtrip unchanged, including non-Latin names and a number with spacing.
        ArrayList<Contacts.Entry> saved = new ArrayList<>();
        saved.add(Contacts.entry("id-1","Asha Patel","+91 98250 12345","Landlord"));
        saved.add(Contacts.entry("id-2","બહેન","079-2630-1122",""));
        saved.add(Contacts.entry("id-3","","1800123456","No name"));
        ArrayList<Contacts.Entry> read = Contacts.decode(Contacts.encode(saved));
        same(3,read.size(),"All records read back");
        for (int i=0;i<saved.size();i++) {
            same(saved.get(i).id,read.get(i).id,"Identifier kept");
            same(saved.get(i).name,read.get(i).name,"Name kept");
            same(saved.get(i).number,read.get(i).number,"Number kept");
            same(saved.get(i).note,read.get(i).note,"Note kept");
        }
        same("Asha Patel",read.get(0).title(),"Named entry titles by name");
        same("1800123456",read.get(2).title(),"Unnamed entry titles by number");

        // Separators typed into a field cannot split or forge a record.
        Contacts.Entry hostile = Contacts.entry("id-4","Line\nbreak\tand tab","+1\t555\n0100","note\twith\nboth");
        same("Line break and tab",hostile.name,"Separators replaced in the name");
        ArrayList<Contacts.Entry> parsed = Contacts.decode(Contacts.encode(Collections.singletonList(hostile)));
        same(1,parsed.size(),"Hostile input is still one record");
        same("+1 555 0100",parsed.get(0).number,"Number survives as one field");
        same("  trimmed  ".trim(),Contacts.entry("i","  trimmed  ","1","").name,"Surrounding spaces trimmed");

        // Unusable records are skipped instead of losing the rest of the list.
        ArrayList<Contacts.Entry> partial = Contacts.decode("id-a\tKeep\t555111\tnote\nbroken-line\n\tNo id\t555222\t\nid-c\tNo number\tnone\t\nid-d\tNo note\t555333");
        same(2,partial.size(),"Damaged records dropped, good ones kept");
        same("Keep",partial.get(0).name,"First good record kept");
        same("",partial.get(1).note,"Missing note reads as empty");
        same(0,Contacts.decode("").size(),"Empty file is an empty list");
        same("",Contacts.encode(new ArrayList<>()),"Empty list encodes to nothing");

        // Only what a tel: URI can carry is dialled, and a number needs at least one digit.
        same("+919825012345",Contacts.dialable("+91 (98250) 12-345"),"Spacing and brackets removed");
        same("*123#",Contacts.dialable("*123#"),"Service codes kept");
        same("5551234,,99",Contacts.dialable("555-1234,,99"),"Pause characters kept");
        same("",Contacts.dialable("no digits here"),"Text without digits is not dialable");
        same("",Contacts.dialable(""),"Empty number is not dialable");
        same("",Contacts.dialable(null),"Missing number is not dialable");
        System.out.println("Contacts: " + checks + " record format and dialling checks passed");
    }
    private static void same(Object expected, Object actual, String description) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(description + ": expected <" + expected + "> but was <" + actual + ">");
    }
}
