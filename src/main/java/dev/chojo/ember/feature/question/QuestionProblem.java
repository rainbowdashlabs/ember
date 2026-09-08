/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

/**
 * Why an answer is not one this question takes.
 *
 * <p>Returned rather than thrown, because each feature refuses in its own words and through its own
 * kind of failure: a route answers with a bad request, a batch collects the lines it could not read,
 * and an import says which row it stopped at.
 *
 * @param code     what is wrong, for whoever wants to decide rather than to read
 * @param question what the question is called
 * @param detail   the part of the answer that is wrong, or what was expected, where saying it helps
 */
public record QuestionProblem(Code code, String question, String detail) {

    /** What can be wrong with an answer. */
    public enum Code {
        /** Nothing was answered and the question has to be. */
        REQUIRED,
        /** The answer is not one of the choices written down. */
        NOT_AN_OPTION,
        /** The answer is not a whole number. */
        NOT_A_NUMBER,
        /** The answer is a number outside what the question allows. */
        OUT_OF_RANGE,
        /** The answer is not a date. */
        NOT_A_DATE,
        /** The answer is not a time of day. */
        NOT_A_TIME,
        /** The answer is neither yes nor no. */
        NOT_A_BOOLEAN,
        /** The answer is not a web address. */
        NOT_A_URL,
        /** The answer does not name a member. */
        NOT_A_MEMBER
    }

    /** What to say to whoever gave the answer. */
    public String message() {
        return switch (code) {
            case REQUIRED -> "Field '" + question + "' is required";
            case NOT_AN_OPTION -> "Field '" + question + "' does not allow the value '" + detail + "'";
            case NOT_A_NUMBER -> "Field '" + question + "' expects a number";
            case OUT_OF_RANGE -> "Field '" + question + "' expects a number " + detail;
            case NOT_A_DATE -> "Field '" + question + "' expects a date";
            case NOT_A_TIME -> "Field '" + question + "' expects a time";
            case NOT_A_BOOLEAN -> "Field '" + question + "' expects yes or no";
            case NOT_A_URL -> "Field '" + question + "' expects a web address";
            case NOT_A_MEMBER -> "Field '" + question + "' expects a member";
        };
    }
}
