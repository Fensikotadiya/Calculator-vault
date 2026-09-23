package com.privatecalc.vault;

public class BrowserTest {
    private static int checks = 0;
    public static void main(String[] args) {
        // An address is taken as typed when it is already https, and upgraded when it is not.
        same("https://example.com/page",Browser.target("https://example.com/page"),"An https address is used as typed");
        same("https://example.com/page",Browser.target("http://example.com/page"),"Cleartext is upgraded to https");
        same("https://Example.com",Browser.target("HTTP://Example.com"),"Scheme is matched without case and the rest is kept");
        same("https://example.com",Browser.target("example.com"),"A bare host becomes https");
        same("https://news.bbc.co.uk/sport",Browser.target("news.bbc.co.uk/sport"),"A host with a path becomes https");
        same("https://example.com:8443/x",Browser.target("example.com:8443/x"),"A port is kept");
        same("https://example.com",Browser.target("   example.com   "),"Surrounding spaces are ignored");

        // Anything that is not an address is searched for, so no other scheme can be loaded.
        same("https://www.google.com/search?q=hello+world",Browser.target("hello world"),"Words are searched for");
        same("https://www.google.com/search?q=example",Browser.target("example"),"A word without a dot is searched for");
        same("https://www.google.com/search?q=2%2B2",Browser.target("2+2"),"A search is percent encoded");
        starts("https://www.google.com/search?q=",Browser.target("javascript:alert(1)"),"javascript: is searched for, never run");
        starts("https://www.google.com/search?q=",Browser.target("file:///etc/hosts"),"file: is searched for, never opened");
        starts("https://www.google.com/search?q=",Browser.target("data:text/html,<b>x"),"data: is searched for");
        starts("https://www.google.com/search?q=",Browser.target("intent://evil#Intent;end"),"intent: is searched for");
        starts("https://www.google.com/search?q=",Browser.target("JavaScript:alert(1)"),"Scheme case does not slip past the check");
        same(Browser.HOME,Browser.target(""),"An empty line opens the home page");
        same(Browser.HOME,Browser.target("   "),"A blank line opens the home page");
        same(Browser.HOME,Browser.target(null),"A missing line opens the home page");
        starts("https://",Browser.target("https://example.com"),"Every result is https");

        // The site shown beside the address is the host that actually served the page.
        same("example.com",Browser.host("https://example.com/a/b?c=d#e"),"Path, query and fragment are dropped");
        same("example.com",Browser.host("https://www.example.com/"),"A www prefix is dropped");
        same("attacker.test",Browser.host("https://bank.example@attacker.test/login"),"Anything before an @ is dropped");
        same("attacker.test",Browser.host("https://a@b@attacker.test/"),"Only the last @ separates the host");
        same("example.com",Browser.host("https://example.com:8443/x"),"A port is not shown");
        same("example.com",Browser.host("example.com/x"),"A missing scheme still reads as a host");
        same("",Browser.host(""),"An empty address has no host");
        same("",Browser.host(null),"A missing address has no host");

        // Pages the browser loads itself are never shown as an address.
        same(true,Browser.blank(null),"A missing address is blank");
        same(true,Browser.blank(""),"An empty address is blank");
        same(true,Browser.blank("about:blank"),"The blank page is blank");
        same(false,Browser.blank("https://example.com"),"A real page is not blank");
        System.out.println("Browser: " + checks + " address and host checks passed");
    }
    private static void same(Object expected, Object actual, String description) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(description + ": expected <" + expected + "> but was <" + actual + ">");
    }
    private static void starts(String prefix, String actual, String description) {
        checks++;
        if (actual == null || !actual.startsWith(prefix)) throw new AssertionError(description + ": expected a value starting <" + prefix + "> but was <" + actual + ">");
    }
}
