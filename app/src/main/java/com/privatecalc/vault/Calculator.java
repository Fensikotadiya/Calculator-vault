package com.privatecalc.vault;

import java.math.BigDecimal;

/** Small recursive descent parser: precedence, unary signs, percentage, powers, brackets and the usual scientific functions. Angles are degrees. */
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
        double value = unary();
        // A value straight after another one multiplies, so 2(3+4) and 3π read as they are written.
        while (true) { if (eat('*')) value *= unary(); else if (eat('/')) value /= unary(); else if (opens()) value *= unary(); else return value; }
    }
    private double unary() {
        if (eat('+')) return unary();
        if (eat('-')) return -unary();   // looser than a power, so −2^2 is −4
        return power();
    }
    private double power() {
        double value = postfix();
        return eat('^') ? Math.pow(value, unary()) : value;
    }
    private double postfix() {
        double value = primary();
        while (true) { if (eat('%')) value /= 100; else if (eat('²')) value *= value; else return value; }
    }
    private boolean opens() {
        if (position >= source.length()) return false;
        char ch = source.charAt(position);
        return Character.isDigit(ch) || Character.isLetter(ch) || ch == '.' || ch == '(' || ch == 'π' || ch == '√';
    }
    private double primary() {
        if (eat('(')) { double value = expression(); if (!eat(')')) throw new IllegalArgumentException("Missing closing bracket"); return value; }
        if (eat('π')) return Math.PI;
        if (eat('√')) return Math.sqrt(unary());
        if (position < source.length() && Character.isLetter(source.charAt(position))) return function();
        int start = position;
        while (position < source.length() && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) position++;
        if (start == position) throw new IllegalArgumentException("Missing number");
        return Double.parseDouble(source.substring(start, position));
    }
    private double function() {
        int start = position;
        while (position < source.length() && Character.isLetter(source.charAt(position))) position++;
        String name = source.substring(start, position);
        if (name.equals("e")) return Math.E;
        double value = eat('-') ? -primary() : primary();   // sin(30)² squares the sine, sin−30 still reads as a negative angle
        switch (name) {
            case "sin": return snap(Math.sin(Math.toRadians(value)));
            case "cos": return snap(Math.cos(Math.toRadians(value)));
            case "tan":
                if (snap(Math.cos(Math.toRadians(value))) == 0) throw new IllegalArgumentException("Undefined at " + value + "°");
                return snap(Math.tan(Math.toRadians(value)));
            case "log": return Math.log10(value);
            case "ln": return Math.log(value);
            default: throw new IllegalArgumentException("Unknown function " + name);
        }
    }
    /** Trigonometry of exact angles lands a rounding step away from 0, 0.5 or 1; pull those back. */
    private static double snap(double value) {
        double rounded = Math.round(value * 1e12) / 1e12;
        return Math.abs(value - rounded) < 1e-12 ? rounded : value;
    }
}
