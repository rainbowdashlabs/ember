/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import dev.chojo.ember.util.Json;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * How an answer is written down, and how it is read back.
 *
 * <p>One member is a bare number, several are a JSON array, and both shapes are tolerated on the way
 * in because both have been stored. Appointments and attendance sheets each had their own copy of
 * this, which is two answers to a question that has one.
 *
 * <p>The same holds for every other answer: a JSON column and a text column each hold one shape per
 * kind, written by {@link #write} and {@link #writeText}, and {@link #read} takes every shape that
 * was ever stored and gives back the plain text the check and the screens work with.
 */
public final class QuestionValues {
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private QuestionValues() {}

    /**
     * The members an answer names, in the order it names them.
     *
     * <p>Forgiving on purpose: a stored answer is whatever was once written into it, and a list half
     * of which reads as members is still those members to everybody showing it. Checking an answer
     * on its way in asks the stricter question, which is {@link #namesOnlyMembers(String)}.
     *
     * @param value the answer as it is stored
     * @return the member ids it names, empty where it names none
     */
    public static List<Integer> memberIds(String value) {
        var ids = new ArrayList<Integer>();
        for (String part : parts(value)) {
            try {
                ids.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return ids;
    }

    /**
     * Whether an answer names members and nothing else, which is the stricter question checking one
     * asks: reading is forgiving so that an old answer still shows, and writing is not.
     *
     * @param value the answer being given
     * @return true where every part of it is a member
     */
    public static boolean namesOnlyMembers(String value) {
        var parts = parts(value);
        if (parts.isEmpty()) return false;
        return parts.size() == memberIds(value).size();
    }

    /** The pieces an answer is made of, whichever of the two shapes it was written in. */
    private static List<String> parts(String value) {
        if (value == null || value.isBlank()) return List.of();
        String cleaned = value.trim();
        if (cleaned.startsWith("[")) {
            cleaned = cleaned.replaceAll("[\\[\\]\"\\s]", "");
            if (cleaned.isBlank()) return List.of();
            return Arrays.stream(cleaned.split(","))
                    .map(String::trim)
                    .filter(part -> !part.isBlank())
                    .toList();
        }
        cleaned = cleaned.replace("\"", "").trim();
        return cleaned.isBlank() ? List.of() : List.of(cleaned);
    }

    /**
     * An answer as plain text, whichever way the feature that holds it writes it down.
     *
     * <p>Three of them keep their answers as JSON, so a line of text arrives wrapped in quotes and a
     * date reads as {@code "2011-09-01"} rather than as a date. Checking one has to see what
     * somebody actually typed.
     *
     * @param stored the answer as the feature holds it
     * @return the same answer with nothing around it
     */
    public static String text(@Nullable String stored) {
        if (stored == null) return "";
        String trimmed = stored.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /**
     * What was actually said, which is nothing in more spellings than one.
     *
     * <p>The features that keep their answers as JSON write an unanswered question as the literal
     * {@code null} or as an empty string in quotes, and both mean the same as an empty box.
     *
     * @param stored the answer as the feature holds it
     * @return the answer as plain text, empty where nothing was said
     */
    public static String said(@Nullable String stored) {
        String text = text(stored);
        return text.equalsIgnoreCase("null") ? "" : text;
    }

    /**
     * A stored answer as plain text, whichever shape a JSON column holds it in.
     *
     * <p>A yes may be stored as {@code true} or as {@code "true"}, one member as {@code 5} or as
     * {@code "5"}, and nothing as JSON {@code null}, an empty string or the empty object a column
     * defaults to. All of them read the same.
     *
     * @param stored the column's document, or null where there is no row
     * @return the answer as plain text, empty where nothing was said; several members read as
     *     {@code [1,2]}
     */
    public static String read(@Nullable JsonNode stored) {
        if (stored == null || stored.isNull() || stored.isMissingNode()) return "";
        if (stored.isString()) return said(stored.asString());
        if (stored.isBoolean() || stored.isNumber()) return stored.asString();
        if (stored.isObject() && stored.isEmpty()) return "";
        if (stored.isArray()) return arrayText(stored);
        return stored.toString();
    }

    /**
     * A stored answer as plain text, from a text column or from a JSON column read as text.
     *
     * <p>A text column holds the answer bare, a JSON column holds a document; what starts like a
     * document is read as one, anything else is already the answer.
     *
     * @param stored the column as it stands, or null
     * @return the answer as plain text, empty where nothing was said
     */
    public static String read(@Nullable String stored) {
        if (stored == null) return "";
        String trimmed = stored.trim();
        if (trimmed.startsWith("[") || trimmed.startsWith("{") || trimmed.startsWith("\"")) {
            try {
                return read(Json.MAPPER.readTree(trimmed));
            } catch (JacksonException notADocument) {
                return said(trimmed);
            }
        }
        return said(trimmed);
    }

    /**
     * An answer in the one shape a JSON column holds for its type.
     *
     * <p>Yes or no is a boolean, a number a number, one member a number, several members an array of
     * numbers, and everything else a string. Nothing is stored for an empty answer or for a type that
     * holds no value, which is why this may return null: the row is left out rather than written
     * empty.
     *
     * @param type   the field type
     * @param answer the answer as plain text, as the check takes it
     * @return the document to store, or null where nothing is stored
     */
    public static @Nullable JsonNode write(FieldType type, @Nullable String answer) {
        String value = said(answer);
        var kind = type.kind();
        if (value.isEmpty() || kind.isEmpty()) return null;
        return switch (kind.get()) {
            case BOOLEAN -> bool(value);
            case NUMBER, DECIMAL -> number(value);
            case MEMBER -> member(value);
            case MEMBER_LIST -> members(value);
            default -> NODES.stringNode(value);
        };
    }

    /**
     * An answer in the one shape a text column holds for its type: the same as {@link #write}, with
     * a string written bare and nothing written as the empty string the column defaults to.
     *
     * @param type   the field type
     * @param answer the answer as plain text
     * @return the text to store
     */
    public static String writeText(FieldType type, @Nullable String answer) {
        JsonNode node = write(type, answer);
        if (node == null) return "";
        return node.isString() ? node.asString() : node.toString();
    }

    private static String arrayText(JsonNode array) {
        var ids = new ArrayList<Integer>();
        for (JsonNode element : array) {
            String part = element.isString() ? element.asString().trim() : element.toString();
            try {
                ids.add(Integer.parseInt(part));
            } catch (NumberFormatException notAMember) {
                return array.toString();
            }
        }
        return formatMembers(ids);
    }

    private static JsonNode bool(String value) {
        if (value.equalsIgnoreCase("true") || value.equals("1")) return NODES.booleanNode(true);
        if (value.equalsIgnoreCase("false") || value.equals("0")) return NODES.booleanNode(false);
        return NODES.stringNode(value);
    }

    private static JsonNode number(String value) {
        try {
            return NODES.numberNode(new BigDecimal(value.replace(',', '.')));
        } catch (NumberFormatException notANumber) {
            return NODES.stringNode(value);
        }
    }

    private static JsonNode member(String value) {
        var ids = memberIds(value);
        return ids.size() == 1 && namesOnlyMembers(value) ? NODES.numberNode(ids.getFirst()) : NODES.stringNode(value);
    }

    private static @Nullable JsonNode members(String value) {
        if (parts(value).isEmpty()) return null;
        if (!namesOnlyMembers(value)) return NODES.stringNode(value);
        var array = NODES.arrayNode();
        for (int id : memberIds(value)) {
            array.add(id);
        }
        return array;
    }

    /** Several members as an answer stores them. */
    public static String formatMembers(List<Integer> ids) {
        var written = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) written.append(",");
            written.append(ids.get(i));
        }
        return written.append("]").toString();
    }

    /** One member as an answer stores them, empty where there is none. */
    public static String formatMember(Integer id) {
        return id == null ? "" : String.valueOf(id);
    }
}
