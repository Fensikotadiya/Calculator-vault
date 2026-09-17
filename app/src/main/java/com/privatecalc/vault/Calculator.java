package com.privatecalc.vault;

import java.math.BigDecimal;

/** Small recursive descent parser: precedence, unary signs and percentage. */
public final class Calculator {
    private final String source;
    private int position;
    private Calculator(String source) { this.source = source; }
    public static String evaluate(String source) {
        Calculator parser = new Calculator(source.replace('×', '*').replace('÷', '/').replace('−', '-'));
        double value = parser.expression();
        if (parser.position != parser.source.length() || !Double.isFinite(value)) throw new IllegalArgumentException("Invalid calculation");
        return BigDecimal.valueOf(value).round(new java.math.MathContext(12)).stripTrailingZeros().toPlainString();
    }
    private boolean eat(char ch) {
        if (position < source.length() && source.charAt(position) == ch) { position++; return true; }
        return false;
    }
    private double expression() {
        double value = term();
        while (true) { if (eat('+')) value += term(); else if (eat('-')) value -= term(); else return value; }
    }
    private double term() {
        double value = number();
        while (true) { if (eat('*')) value *= number(); else if (eat('/')) value /= number(); else return value; }
    }
    private double number() {
        if (eat('+')) return number();
        if (eat('-')) return -number();
        int start = position;
        while (position < source.length() && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) position++;
        if (start == position) throw new IllegalArgumentException("Missing number");
        double value = Double.parseDouble(source.substring(start, position));
        while (eat('%')) value /= 100;
        return value;
    }
}
