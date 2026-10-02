/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#QUIZZES}: quizzes.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum QuizRefusal implements Refusal {
    /** An answer written on a paper that is no longer being sat. */
    QUIZ_ALREADY_HANDED_IN(
            1,
            HttpStatus.CONFLICT,
            "This paper has already been handed in, so the answer was not saved. "
                    + "Reload the page to see it as it stands"),

    /** A paper handed in that was not still being written, whether already in or out of time. */
    QUIZ_NOT_HANDED_IN(
            2,
            HttpStatus.CONFLICT,
            "This paper could not be handed in: it is already in, or the time for it has run out. "
                    + "Reload the page to see where it stands"),

    /** A paper that went between being handed in and being read back. */
    QUIZ_ATTEMPT_NOT_HERE_AFTER_HANDING_IN(3, HttpStatus.NOT_FOUND, Sentences.QUIZ_ATTEMPT_NOT_HERE),

    /** A question that is gone, or belongs to another station's catalog. */
    QUIZ_QUESTION_NOT_HERE(4, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** An attempt that is gone, asked for by a route that then checks whose test it was. */
    QUIZ_ATTEMPT_NOT_HERE(5, HttpStatus.NOT_FOUND, Sentences.QUIZ_ATTEMPT_NOT_HERE),

    /** An attempt that is gone, asked for by the member sitting it. */
    QUIZ_ATTEMPT_NOT_HERE_FOR_MEMBER(6, HttpStatus.NOT_FOUND, Sentences.QUIZ_ATTEMPT_NOT_HERE),

    /** A test attempt that belongs to somebody else. */
    QUIZ_ATTEMPT_NOT_YOURS(7, HttpStatus.FORBIDDEN, "That attempt is somebody else's"),

    /** A catalog that went between the list being drawn and it being opened. */
    QUIZ_CATALOG_NOT_HERE(8, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATALOG_NOT_HERE),

    /** A catalog that went before the change to it could be written. */
    QUIZ_CATALOG_NOT_CHANGED(9, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATALOG_NOT_HERE),

    /** A catalog that went between being changed and being read back. */
    QUIZ_CATALOG_NOT_HERE_AFTER_CHANGE(10, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATALOG_NOT_HERE),

    /** A catalog that was already gone when its deletion was asked for. */
    QUIZ_CATALOG_NOT_DELETED(11, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATALOG_NOT_HERE),

    /** A grouping of questions that went before the change to it could be written. */
    QUIZ_CATEGORY_NOT_CHANGED(12, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATEGORY_NOT_HERE),

    /** A grouping of questions that was already gone when its deletion was asked for. */
    QUIZ_CATEGORY_NOT_DELETED(13, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATEGORY_NOT_HERE),

    /** An example file asked for in a format there is no example in. */
    QUIZ_TEMPLATE_FORMAT_UNKNOWN(14, HttpStatus.NOT_FOUND, "There is no example file in that format"),

    /** A test that went before the change to it could be written. */
    QUIZ_TEST_NOT_CHANGED(15, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A test that went between being changed and being read back. */
    QUIZ_TEST_NOT_HERE_AFTER_CHANGE(16, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A test that was already gone when its deletion was asked for. */
    QUIZ_TEST_NOT_DELETED(17, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A test that went between being started and being read back. */
    QUIZ_TEST_NOT_HERE_AFTER_ACTIVATION(18, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A test that went before it could be closed. */
    QUIZ_TEST_NOT_CLOSED(19, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A test that went between being closed and being read back. */
    QUIZ_TEST_NOT_HERE_AFTER_CLOSING(20, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A catalog named in a public teaser link by something that is not a number. */
    PUBLIC_QUIZ_CATALOG_NOT_A_NUMBER(
            21, HttpStatus.BAD_REQUEST, "The catalogs in that link must each be named by a number"),

    /**
     * A station named in a public teaser link by something that is not an identifier. It shares its
     * sentence with the miss below on purpose: the reader is a stranger on the open internet and is
     * owed nothing about whether a station answers to the identifier they hold.
     */
    PUBLIC_QUIZ_STATION_LINK_NOT_GOOD(22, HttpStatus.BAD_REQUEST, Sentences.PUBLIC_QUIZ_STATION_NOT_REACHED),

    /** A public teaser link naming a station that is not here. */
    PUBLIC_QUIZ_STATION_NOT_HERE(23, HttpStatus.NOT_FOUND, Sentences.PUBLIC_QUIZ_STATION_NOT_REACHED),

    /** A public teaser asking for a question without saying which catalogs to draw it from. */
    PUBLIC_QUIZ_CATALOGS_NOT_NAMED(24, HttpStatus.BAD_REQUEST, Sentences.PUBLIC_QUIZ_CATALOGS_NOT_NAMED),

    /** A public teaser naming catalogs that come to nothing once read. */
    PUBLIC_QUIZ_CATALOGS_EMPTY(25, HttpStatus.BAD_REQUEST, Sentences.PUBLIC_QUIZ_CATALOGS_NOT_NAMED),

    /** No public question in any of the catalogs a teaser asked for. */
    PUBLIC_QUIZ_NO_QUESTION_HERE(26, HttpStatus.NOT_FOUND, "There is no question here to show from those catalogs"),

    /** A test whose questions or sections were being changed while it was being sat. */
    QUIZ_TEST_RUNNING_CANNOT_CHANGE(27, HttpStatus.BAD_REQUEST, "This test is running, so nothing was changed"),

    /** A catalog written down without a name. */
    QUIZ_CATALOG_NEEDS_A_NAME(29, HttpStatus.BAD_REQUEST, "A catalog needs a name, so nothing was saved"),

    /** A category written down without a name. */
    QUIZ_CATEGORY_NEEDS_A_NAME(30, HttpStatus.BAD_REQUEST, "A category needs a name, so nothing was saved"),

    /** Training asked for on a catalog that is not handed out for training. */
    QUIZ_CATALOG_NOT_FOR_TRAINING(31, HttpStatus.FORBIDDEN, "This catalog is not open for training"),

    /** A sheet sent to be read into questions with nothing in it. */
    QUIZ_SHEET_EMPTY(32, HttpStatus.BAD_REQUEST, "The sheet arrived empty, so nothing was read from it"),

    /** A sheet sent to be read into questions without saying which column holds what. */
    QUIZ_SHEET_COLUMNS_NOT_MAPPED(
            33, HttpStatus.BAD_REQUEST, "Say which column holds what, so nothing was read from it"),

    /** A note about a question that is gone, or belongs to another station's catalog. */
    QUIZ_QUESTION_NOTE_NOT_HERE(34, HttpStatus.NOT_FOUND, Sentences.QUIZ_QUESTION_NOTE_NOT_HERE),

    /** A note about a question that was already gone when it was marked as dealt with. */
    QUIZ_QUESTION_NOTE_NOT_CLEARED(35, HttpStatus.NOT_FOUND, Sentences.QUIZ_QUESTION_NOTE_NOT_HERE),

    /** A question written down without a title. */
    QUIZ_QUESTION_NEEDS_A_TITLE(36, HttpStatus.BAD_REQUEST, "A question needs a title, so nothing was saved"),

    /** A question written down without saying what kind of question it is. */
    QUIZ_QUESTION_NEEDS_A_KIND(37, HttpStatus.BAD_REQUEST, "A question needs a kind, so nothing was saved"),

    /** A question that went before the change to it could be written. */
    QUIZ_QUESTION_NOT_CHANGED(38, HttpStatus.NOT_FOUND, Sentences.QUIZ_QUESTION_NOT_HERE_ON_WRITE),

    /** A question that went between being changed and being read back, with the change already in. */
    QUIZ_QUESTION_NOT_HERE_AFTER_CHANGE(
            39,
            HttpStatus.NOT_FOUND,
            "The change to that question was saved, but it could not be read back. "
                    + "Reload the page to see it as it stands"),

    /** A question that was already gone when its deletion was asked for. */
    QUIZ_QUESTION_NOT_DELETED(40, HttpStatus.NOT_FOUND, Sentences.QUIZ_QUESTION_NOT_HERE_ON_WRITE),

    /** A picture asked for on a question that carries none. */
    QUIZ_QUESTION_PICTURE_NOT_HERE(41, HttpStatus.NOT_FOUND, "That question has no picture"),

    /** A picture for a question sent without the picture in it. */
    QUIZ_PICTURE_UPLOAD_WITHOUT_FILE(42, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A picture for a question sent in a format that is not taken here. */
    QUIZ_PICTURE_KIND_NOT_TAKEN(43, HttpStatus.BAD_REQUEST, Sentences.KB_PICTURE_KIND_NOT_TAKEN),

    /** A picture for a question that could not be taken as it was sent. */
    QUIZ_PICTURE_NOT_SAVED(
            44, HttpStatus.BAD_REQUEST, "That picture could not be saved, so the question keeps the one it had"),

    /** A picture for a question that could not be worked through at all. */
    QUIZ_PICTURE_NOT_PROCESSED(45, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UPLOAD_NOT_PROCESSED),

    /** A test started by somebody it is not open to, whether by time or by who may sit it. */
    QUIZ_TEST_NOT_OPEN_TO_YOU(47, HttpStatus.FORBIDDEN, "This test is not open to you now, so no attempt was started"),

    /** An answer on a paper that is gone, reached for by somebody marking it. */
    QUIZ_ANSWER_NOT_HERE(49, HttpStatus.NOT_FOUND, "That answer is not here any more"),

    /** A mark given for an answer without saying how many points it is worth. */
    QUIZ_GRADE_NEEDS_POINTS(50, HttpStatus.BAD_REQUEST, "Give the points for this answer, so no mark was saved"),

    /** A paper that went between its marks being written and being read back, with the marks in. */
    QUIZ_ATTEMPT_NOT_HERE_AFTER_GRADING(
            52,
            HttpStatus.NOT_FOUND,
            "The marks were saved, but that attempt could not be read back. "
                    + "Reload the page to see it as it stands"),

    /** A test written down without a title. */
    QUIZ_TEST_NEEDS_A_TITLE(54, HttpStatus.BAD_REQUEST, "A test needs a title, so nothing was saved"),

    /** A test started that was not waiting to be started. */
    QUIZ_TEST_NOT_A_DRAFT(55, HttpStatus.BAD_REQUEST, "This test has already been started, so nothing was changed"),

    /** Questions drawn again for a test that is being sat. */
    QUIZ_TEST_RUNNING_CANNOT_REDRAW(
            56, HttpStatus.BAD_REQUEST, "This test is running, so its questions cannot be drawn again"),

    /** A test opened for somebody without saying who. */
    QUIZ_TEST_ACCESS_NEEDS_A_MEMBER(
            57, HttpStatus.BAD_REQUEST, "Name the member to open this test for, so nothing was saved"),

    /** A test that could not be made into a question sheet. */
    QUIZ_TEST_PDF_NOT_MADE(
            58, HttpStatus.INTERNAL_SERVER_ERROR, "That test could not be made into a PDF. Trying again may work"),

    /** A test that could not be made into an answer sheet. */
    QUIZ_TEST_SOLUTION_PDF_NOT_MADE(
            59,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The answer sheet could not be made into a PDF. Trying again may work"),

    /** A provider for generated questions written down without a key. */
    AI_PROVIDER_NEEDS_A_KEY(60, HttpStatus.BAD_REQUEST, "Give the key for this provider, so nothing was saved"),

    /** A provider that turned the key or the request away when its models were asked for. */
    AI_MODELS_NOT_LISTED_KEY_NOT_GOOD(
            61, HttpStatus.BAD_REQUEST, "The models could not be listed: check the key and the provider"),

    /** A provider that could not be reached at all when its models were asked for. */
    AI_MODELS_NOT_LISTED(62, HttpStatus.BAD_REQUEST, "The models could not be listed. Trying again may work"),

    /** Answers asked to be generated without the question they belong to. */
    AI_GENERATION_NEEDS_A_QUESTION(
            63, HttpStatus.BAD_REQUEST, "Give the question to work from, so nothing was generated"),

    /** Answers asked to be generated without the right answer to work around. */
    AI_GENERATION_NEEDS_THE_RIGHT_ANSWER(
            64, HttpStatus.BAD_REQUEST, "Give the right answer to work from, so nothing was generated"),

    /** A provider that turned the key, the model or the request away while generating. */
    AI_GENERATION_REFUSED(
            65,
            HttpStatus.BAD_REQUEST,
            "Nothing could be generated from that: check the key, the model and what was asked for"),

    /** A provider that could not be reached at all while generating. */
    AI_GENERATION_FAILED(66, HttpStatus.BAD_REQUEST, "Nothing could be generated this time. Trying again may work"),

    /** Questions asked to be generated without saying what to generate. */
    AI_GENERATION_NEEDS_ENTRIES(
            67, HttpStatus.BAD_REQUEST, "Say what to generate and how much, so nothing was generated"),

    /** A generation whose results were already collected, or that another station started. */
    AI_GENERATION_NOT_HERE(
            68, HttpStatus.NOT_FOUND, "That generation is no longer here, so its questions cannot be collected"),

    /**
     * A catalog asked for by a paired instance that is not here, belongs to a third station or is
     * not among the ones shared with the asker. One code deliberately: telling the three apart
     * would let a partner count its way through a question bank it was never given.
     */
    REMOTE_QUIZ_CATALOG_NOT_SHARED(69, HttpStatus.NOT_FOUND, "No catalog here is shared with you under that number"),

    /** An AI provider named in an address that is none this instance can call. */
    AI_KEY_PROVIDER_UNKNOWN(70, HttpStatus.BAD_REQUEST, "That AI provider is not one this instance can use"),

    /**
     * A personal AI setting saved without a key, where none is stored for that provider to keep: the
     * first save, a change of provider, or a stored key that no longer opens.
     */
    AI_KEY_MISSING(71, HttpStatus.BAD_REQUEST, "Enter the key for this provider, so nothing was saved"),

    /** A personal AI key that was saved and then could not be read back to answer with. */
    AI_KEY_NOT_READ_BACK(72, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A catalog to generate questions or answers for that is gone or belongs to another station. */
    AI_GENERATION_CATALOG_NOT_HERE(73, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATALOG_NOT_HERE),

    /** The example catalog sheet Ember ships, missing from the build. Ours to look into. */
    QUIZ_CATALOG_EXAMPLE_MISSING(74, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.QUIZ_CATALOG_EXAMPLE_NOT_READ),

    /** The example catalog sheet Ember ships, found but not read. Ours to look into. */
    QUIZ_CATALOG_EXAMPLE_NOT_READ(75, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.QUIZ_CATALOG_EXAMPLE_NOT_READ),

    /** A catalog file uploaded that is not a single object at all. */
    QUIZ_CATALOG_FILE_NOT_AN_OBJECT(76, HttpStatus.BAD_REQUEST, Sentences.QUIZ_CATALOG_FILE_NOT_ONE),

    /** A catalog file uploaded that is neither the current kind nor the one earlier versions wrote. */
    QUIZ_CATALOG_FILE_NOT_RECOGNISED(77, HttpStatus.BAD_REQUEST, Sentences.QUIZ_CATALOG_FILE_NOT_ONE),

    /** A catalog file of the current kind whose contents do not fit it. */
    QUIZ_CATALOG_FILE_NOT_READ(78, HttpStatus.BAD_REQUEST, Sentences.QUIZ_CATALOG_FILE_NOT_ONE),

    /** A note about a question with nothing written in it. */
    QUIZ_QUESTION_NOTE_EMPTY(79, HttpStatus.BAD_REQUEST, "Say what is wrong with the question, so nothing was sent"),

    /** A sheet of questions to import that could not be read as a table. */
    QUIZ_IMPORT_SHEET_NOT_READ(
            80, HttpStatus.BAD_REQUEST, "The sheet could not be read as a table, so nothing was imported"),

    /** A row of a sheet to import naming a kind of question Ember does not know. */
    QUIZ_IMPORT_QUESTION_TYPE_UNKNOWN(
            81, HttpStatus.BAD_REQUEST, "A row names a kind of question Ember does not know, so nothing was imported"),

    /** A sheet to import without the column chosen to carry the questions. */
    QUIZ_IMPORT_QUESTION_COLUMN_MISSING(
            82,
            HttpStatus.BAD_REQUEST,
            "The sheet has no column with the name chosen for the questions, so nothing was imported");

    private final Definition definition;

    QuizRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.QUIZZES, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
