/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import dev.chojo.ember.util.Json;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Every stored shape an answer was found in, read back as the same text, and the one shape each kind
 * is written in.
 */
class QuestionValuesReadWriteTest {

    private static JsonNode json(String document) {
        return Json.MAPPER.readTree(document);
    }

    @Test
    void yesReadsTheSameInEveryStoredShape() {
        assertEquals("true", QuestionValues.read(json("true")));
        assertEquals("true", QuestionValues.read(json("\"true\"")));
        assertEquals("true", QuestionValues.read("true"));
        assertEquals("true", QuestionValues.read("\"true\""));
    }

    @Test
    void oneMemberReadsTheSameAsNumberOrString() {
        assertEquals("6", QuestionValues.read(json("6")));
        assertEquals("6", QuestionValues.read(json("\"6\"")));
        assertEquals("6", QuestionValues.read("6"));
    }

    @Test
    void severalMembersReadAsOneList() {
        assertEquals("[6,5]", QuestionValues.read(json("[6, 5]")));
        assertEquals("[6,5]", QuestionValues.read(json("[\"6\", \"5\"]")));
        assertEquals("[6,5]", QuestionValues.read("[6,5]"));
        assertEquals("[]", QuestionValues.read(json("[]")));
    }

    @Test
    void anArrayOfAnythingElseReadsAsItsDocument() {
        assertEquals("[\"a\",\"b\"]", QuestionValues.read(json("[\"a\", \"b\"]")));
    }

    @Test
    void nothingReadsEmptyInEveryStoredShape() {
        assertEquals("", QuestionValues.read((JsonNode) null));
        assertEquals("", QuestionValues.read(json("null")));
        assertEquals("", QuestionValues.read(json("\"\"")));
        assertEquals("", QuestionValues.read(json("{}")));
        assertEquals("", QuestionValues.read(JsonNodeFactory.instance.missingNode()));
        assertEquals("", QuestionValues.read((String) null));
        assertEquals("", QuestionValues.read("null"));
        assertEquals("", QuestionValues.read(""));
        assertEquals("", QuestionValues.read("{}"));
    }

    @Test
    void textReadsWithoutItsQuotes() {
        assertEquals("Müller", QuestionValues.read(json("\"Müller\"")));
        assertEquals("Müller", QuestionValues.read("Müller"));
        assertEquals("2011-09-01", QuestionValues.read("2011-09-01"));
        assertEquals("2.5", QuestionValues.read(json("2.5")));
    }

    /** A text column holding something that only looks like a document is still the answer. */
    @Test
    void aBrokenDocumentInATextColumnIsTheAnswer() {
        assertEquals("[Ausweis", QuestionValues.read("[Ausweis"));
    }

    @Test
    void aNonEmptyObjectReadsAsItsDocument() {
        assertEquals("{\"a\":1}", QuestionValues.read(json("{\"a\": 1}")));
    }

    @Test
    void yesAndNoAreWrittenAsBooleans() {
        assertEquals(json("true"), QuestionValues.write(FieldType.BOOLEAN, "true"));
        assertEquals(json("true"), QuestionValues.write(FieldType.BOOLEAN, "1"));
        assertEquals(json("false"), QuestionValues.write(FieldType.BOOLEAN, "\"false\""));
        assertEquals(json("false"), QuestionValues.write(FieldType.BOOLEAN, "0"));
        assertEquals(json("\"vielleicht\""), QuestionValues.write(FieldType.BOOLEAN, "vielleicht"));
    }

    @Test
    void aNumberIsWrittenAsANumber() {
        assertEquals("2.5", QuestionValues.write(FieldType.NUMBER, "2,5").toString());
        assertEquals("4", QuestionValues.write(FieldType.NUMBER, "4").toString());
        assertEquals(json("\"viele\""), QuestionValues.write(FieldType.NUMBER, "viele"));
    }

    @Test
    void membersAreWrittenAsNumbers() {
        assertEquals(json("6"), QuestionValues.write(FieldType.MEMBER_OF_GROUP, "\"6\""));
        assertEquals(json("6"), QuestionValues.write(FieldType.LANE_ASSIGNEE, "6"));
        assertEquals(json("\"Paul\""), QuestionValues.write(FieldType.MEMBER, "Paul"));
        assertEquals(json("[6,5]"), QuestionValues.write(FieldType.MEMBER_LIST, "[\"6\",\"5\"]"));
        assertEquals(json("[6]"), QuestionValues.write(FieldType.MEMBER_LIST_OF_TAG, "6"));
        assertEquals(json("\"[Paul]\""), QuestionValues.write(FieldType.MEMBER_LIST, "[Paul]"));
        assertNull(QuestionValues.write(FieldType.MEMBER_LIST, "[]"));
    }

    @Test
    void everythingElseIsWrittenAsAString() {
        assertEquals(json("\"2011-09-01\""), QuestionValues.write(FieldType.BIRTH_DATE, "2011-09-01"));
        assertEquals(json("\"Halle 3\""), QuestionValues.write(FieldType.LOCATION, "\"Halle 3\""));
        assertEquals(json("\"M\""), QuestionValues.write(FieldType.CHOICE, "M"));
    }

    @Test
    void nothingIsWrittenForNoAnswerOrATypeThatHoldsNone() {
        assertNull(QuestionValues.write(FieldType.TEXT, ""));
        assertNull(QuestionValues.write(FieldType.TEXT, null));
        assertNull(QuestionValues.write(FieldType.TEXT, "null"));
        assertNull(QuestionValues.write(FieldType.SECTION, "Kopf"));
        assertNull(QuestionValues.write(FieldType.AGE, "12"));
    }

    @Test
    void aTextColumnHoldsTheSameShapeBare() {
        assertEquals("true", QuestionValues.writeText(FieldType.BOOLEAN, "1"));
        assertEquals("[1,2]", QuestionValues.writeText(FieldType.MEMBER_LIST_OF_GROUP, "[\"1\",\"2\"]"));
        assertEquals("6", QuestionValues.writeText(FieldType.MEMBER, "\"6\""));
        assertEquals("Mischkost", QuestionValues.writeText(FieldType.CHOICE, "Mischkost"));
        assertEquals("", QuestionValues.writeText(FieldType.TEXT, ""));
    }

    /** What is written reads back as what was given. */
    @Test
    void writtenAnswersReadBack() {
        assertEquals("[6,5]", QuestionValues.read(QuestionValues.write(FieldType.MEMBER_LIST, "[6,5]")));
        assertEquals("true", QuestionValues.read(QuestionValues.write(FieldType.BOOLEAN, "1")));
        assertEquals("Halle 3", QuestionValues.read(QuestionValues.write(FieldType.TEXT, "Halle 3")));
    }
}
