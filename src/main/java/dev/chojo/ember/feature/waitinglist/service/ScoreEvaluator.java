/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates simple arithmetic/comparison expressions with field variable substitution.
 * Supports: +, -, *, /, ==, !=, &gt;, &lt;, &gt;=, &lt;=, parentheses, ternary ? :, string literals, numbers.
 * Field references use [field_name] syntax.
 */
public final class ScoreEvaluator {

    private ScoreEvaluator() {}

    /**
     * Validates a formula by parsing it with dummy values. Throws IllegalArgumentException if invalid.
     */
    public static void validate(String formula, List<String> fieldNames) {
        if (formula == null || formula.isBlank()) return;
        var dummyVars = new HashMap<String, String>();
        for (var name : fieldNames) {
            dummyVars.put(name, "1");
        }
        // Also add built-in variables
        dummyVars.put("wartezeit_tage", "1");
        dummyVars.put("wartezeit_monate", "1");
        dummyVars.put("wartezeit_quartale", "1");
        dummyVars.put("wartezeit_jahre", "1");
        try {
            // Preprocess age([field]) calls — replace with dummy number
            String processed = formula.replaceAll("age\\(\\[[^]]+]\\)", "1");
            String substituted = substituteVariables(processed, dummyVars);
            var tokens = tokenize(substituted);
            var parser = new Parser(tokens);
            parser.parseTernary();
            if (!(parser.peek() instanceof TEof)) {
                throw new IllegalArgumentException("Unexpected token after expression: " + parser.peek());
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid formula: " + e.getMessage());
        }
    }

    public static double evaluate(String formula, Map<String, String> variables) {
        if (formula == null || formula.isBlank()) return 0.0;
        try {
            String substituted = substituteVariables(formula, variables);
            var tokens = tokenize(substituted);
            var parser = new Parser(tokens);
            return parser.parseTernary();
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String substituteVariables(String formula, Map<String, String> variables) {
        var sb = new StringBuilder();
        int i = 0;
        while (i < formula.length()) {
            if (formula.charAt(i) == '[') {
                int end = formula.indexOf(']', i);
                if (end == -1) throw new IllegalArgumentException("Unclosed [");
                String name = formula.substring(i + 1, end);
                String value = variables.getOrDefault(name, "0");
                sb.append(value);
                i = end + 1;
            } else {
                sb.append(formula.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // --- Tokenizer ---

    private sealed interface Token permits TNum, TStr, TOp, TEof {}

    private record TNum(double value) implements Token {}

    private record TStr(String value) implements Token {}

    private record TOp(String op) implements Token {}

    private record TEof() implements Token {}

    private static List<Token> tokenize(String input) {
        var tokens = new ArrayList<Token>();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '"') {
                int end = input.indexOf('"', i + 1);
                if (end == -1) throw new IllegalArgumentException("Unclosed string");
                tokens.add(new TStr(input.substring(i + 1, end)));
                i = end + 1;
            } else if (Character.isDigit(c)
                    || (c == '.' && i + 1 < input.length() && Character.isDigit(input.charAt(i + 1)))) {
                int start = i;
                while (i < input.length() && (Character.isDigit(input.charAt(i)) || input.charAt(i) == '.')) i++;
                tokens.add(new TNum(Double.parseDouble(input.substring(start, i))));
            } else if (c == '(' || c == ')' || c == '+' || c == '-' || c == '*' || c == '/' || c == '?' || c == ':') {
                tokens.add(new TOp(String.valueOf(c)));
                i++;
            } else if (c == '=' && i + 1 < input.length() && input.charAt(i + 1) == '=') {
                tokens.add(new TOp("=="));
                i += 2;
            } else if (c == '!' && i + 1 < input.length() && input.charAt(i + 1) == '=') {
                tokens.add(new TOp("!="));
                i += 2;
            } else if (c == '>' && i + 1 < input.length() && input.charAt(i + 1) == '=') {
                tokens.add(new TOp(">="));
                i += 2;
            } else if (c == '<' && i + 1 < input.length() && input.charAt(i + 1) == '=') {
                tokens.add(new TOp("<="));
                i += 2;
            } else if (c == '>') {
                tokens.add(new TOp(">"));
                i++;
            } else if (c == '<') {
                tokens.add(new TOp("<"));
                i++;
            } else {
                throw new IllegalArgumentException("Unexpected character: " + c);
            }
        }
        tokens.add(new TEof());
        return tokens;
    }

    // --- Parser (recursive descent) ---

    private static class Parser {
        private final List<Token> tokens;
        private int pos;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
            this.pos = 0;
        }

        private Token peek() {
            return tokens.get(pos);
        }

        private Token advance() {
            return tokens.get(pos++);
        }

        private boolean matchOp(String op) {
            if (peek() instanceof TOp(String op1) && op1.equals(op)) {
                advance();
                return true;
            }
            return false;
        }

        // ternary: comparison ? expression : expression
        double parseTernary() {
            double cond = parseComparison();
            if (matchOp("?")) {
                double ifTrue = parseTernary();
                if (!matchOp(":")) throw new IllegalArgumentException("Expected ':'");
                double ifFalse = parseTernary();
                return cond != 0.0 ? ifTrue : ifFalse;
            }
            return cond;
        }

        // comparison: additive (==|!=|>|<|>=|<= additive)*
        private double parseComparison() {
            double left = parseAdditive();
            while (peek() instanceof TOp(String op1) && isCompOp(op1)) {
                String op = ((TOp) advance()).op();
                double right = parseAdditive();
                left = compareValues(left, op, right);
            }
            return left;
        }

        private boolean isCompOp(String op) {
            return switch (op) {
                case "==", "!=", ">", "<", ">=", "<=" -> true;
                default -> false;
            };
        }

        private double compareValues(double left, String op, double right) {
            return switch (op) {
                case "==" -> left == right ? 1.0 : 0.0;
                case "!=" -> left != right ? 1.0 : 0.0;
                case ">" -> left > right ? 1.0 : 0.0;
                case "<" -> left < right ? 1.0 : 0.0;
                case ">=" -> left >= right ? 1.0 : 0.0;
                case "<=" -> left <= right ? 1.0 : 0.0;
                default -> 0.0;
            };
        }

        // additive: multiplicative ((+|-) multiplicative)*
        private double parseAdditive() {
            double left = parseMultiplicative();
            while (peek() instanceof TOp(String op1) && (op1.equals("+") || op1.equals("-"))) {
                String op = ((TOp) advance()).op();
                double right = parseMultiplicative();
                left = op.equals("+") ? left + right : left - right;
            }
            return left;
        }

        // multiplicative: unary ((*|/) unary)*
        private double parseMultiplicative() {
            double left = parseUnary();
            while (peek() instanceof TOp(String op1) && (op1.equals("*") || op1.equals("/"))) {
                String op = ((TOp) advance()).op();
                double right = parseUnary();
                left = op.equals("*") ? left * right : (right != 0 ? left / right : 0);
            }
            return left;
        }

        // unary: -primary | primary
        private double parseUnary() {
            if (matchOp("-")) {
                return -parsePrimary();
            }
            return parsePrimary();
        }

        // primary: number | string | ( ternary )
        private double parsePrimary() {
            Token t = peek();
            if (t instanceof TNum(double value1)) {
                advance();
                return value1;
            }
            if (t instanceof TStr(String value)) {
                advance();
                // Strings evaluate to a hash for comparison — two equal strings produce the same value
                return value.hashCode();
            }
            if (matchOp("(")) {
                double val = parseTernary();
                if (!matchOp(")")) throw new IllegalArgumentException("Expected ')'");
                return val;
            }
            throw new IllegalArgumentException("Unexpected token: " + t);
        }
    }
}
