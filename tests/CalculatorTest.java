package com.privatecalc.vault;

public class CalculatorTest {
    public static void main(String[] args) {
        check("2+3×4", "14"); check("9÷3−1", "2"); check("−5+2", "-3");
        check("50%×200", "100"); check(".5+.25", "0.75"); check("1−−2", "3");
        check("0.1+0.2", "0.3");
        for (String invalid : new String[]{"", "1÷0", "2+", "1..2", "×3"}) {
            try { Calculator.evaluate(invalid); throw new AssertionError("Accepted " + invalid); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("Calculator: 12 checks passed");
    }
    private static void check(String expression, String expected) {
        String actual = Calculator.evaluate(expression);
        if (!expected.equals(actual)) throw new AssertionError(expression + ": " + actual);
    }
}
