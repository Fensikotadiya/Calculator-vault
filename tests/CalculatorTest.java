package com.privatecalc.vault;

public class CalculatorTest {
    private static int checks;
    public static void main(String[] args) {
        check("2+3×4", "14"); check("9÷3−1", "2"); check("−5+2", "-3");
        check("50%×200", "100"); check(".5+.25", "0.75"); check("1−−2", "3");
        check("0.1+0.2", "0.3");
        check("(2+3)×4", "20"); check("2×(3+(4−1))", "12"); check("((7))", "7");
        check("2^10", "1024"); check("2^3^2", "512"); check("−2^2", "-4"); check("9^0.5", "3");
        check("√9", "3"); check("√(4+12)", "4"); check("5²", "25"); check("(2+1)²", "9");
        check("sin(30)", "0.5"); check("cos(60)", "0.5"); check("sin(180)", "0"); check("tan(45)", "1");
        check("sin(−30)", "-0.5"); check("cos(0)+sin(90)", "2");
        check("log(1000)", "3"); check("ln(1)", "0"); check("log(100)+√16", "6");
        check("2(3+4)", "14"); check("3π", "9.42477796077"); check("2√9", "6"); check("(1+1)(2+2)", "8");
        check("π", "3.14159265359"); check("e", "2.71828182846");
        for (String invalid : new String[]{"", "1÷0", "2+", "1..2", "×3", "(1+2", "1+2)", "2^", "√−4", "log(0)", "tan(90)", "abc(2)", "sin()"}) {
            try { Calculator.evaluate(invalid); throw new AssertionError("Accepted " + invalid); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        System.out.println("Calculator: " + checks + " checks passed");
    }
    private static void check(String expression, String expected) {
        String actual = Calculator.evaluate(expression);
        if (!expected.equals(actual)) throw new AssertionError(expression + ": " + actual);
        checks++;
    }
}
