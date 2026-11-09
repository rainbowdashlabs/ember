/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#PROCEDURES}: procedures.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum ProcedureRefusal implements Refusal {
    /** A procedure template written down without a name. */
    PROCEDURE_TEMPLATE_NEEDS_A_NAME(3, HttpStatus.BAD_REQUEST, Sentences.PROCEDURE_TEMPLATE_NEEDS_A_NAME),

    /** A procedure template renamed to nothing. */
    PROCEDURE_TEMPLATE_RENAME_NEEDS_A_NAME(4, HttpStatus.BAD_REQUEST, Sentences.PROCEDURE_TEMPLATE_NEEDS_A_NAME),

    /** A procedure template that went before the rename reached it. */
    PROCEDURE_TEMPLATE_NOT_HERE_TO_CHANGE(
            5, HttpStatus.NOT_FOUND, "That procedure template is not here any more, so nothing was changed"),

    /** A step added to a procedure template without a title. */
    PROCEDURE_TEMPLATE_STEP_NEEDS_A_TITLE(6, HttpStatus.BAD_REQUEST, Sentences.PROCEDURE_STEP_NEEDS_A_TITLE),

    /** A step of a template that went before the change reached it. */
    PROCEDURE_TEMPLATE_STEP_NOT_HERE_TO_CHANGE(7, HttpStatus.NOT_FOUND, Sentences.PROCEDURE_STEP_NOT_HERE_ON_WRITE),

    /** A step of a template that went before the deletion reached it. */
    PROCEDURE_TEMPLATE_STEP_NOT_HERE_TO_DELETE(8, HttpStatus.NOT_FOUND, Sentences.PROCEDURE_STEP_NOT_HERE_ON_WRITE),

    /** Procedures asked for by appointment without saying which day of it. */
    PROCEDURE_OCCURRENCE_DAY_MISSING(
            9, HttpStatus.BAD_REQUEST, "Name the day of the appointment the procedures were prepared for"),

    /** A day the procedures of an appointment were asked for on that does not read as a date. */
    PROCEDURE_OCCURRENCE_DAY_NOT_A_DATE(10, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** A procedure written down without a name. */
    PROCEDURE_NEEDS_A_NAME(11, HttpStatus.BAD_REQUEST, "A procedure needs a name, so nothing was saved"),

    /** A procedure that went before the change reached it. */
    PROCEDURE_NOT_HERE_TO_CHANGE(
            12, HttpStatus.NOT_FOUND, "That procedure is not here any more, so nothing was changed"),

    /** A procedure marked done that was done already. */
    PROCEDURE_ALREADY_DONE(13, HttpStatus.BAD_REQUEST, "This procedure is already done, so nothing was changed"),

    /** A procedure reopened that was open already. */
    PROCEDURE_ALREADY_OPEN(14, HttpStatus.BAD_REQUEST, "This procedure is already open, so nothing was changed"),

    /** A procedure handed to nobody at all. */
    PROCEDURE_NAMES_NOBODY(
            15, HttpStatus.BAD_REQUEST, "Name at least one member to hand the procedure to, so nothing was saved"),

    /** A step added to a procedure without a title. */
    PROCEDURE_STEP_NEEDS_A_TITLE(16, HttpStatus.BAD_REQUEST, Sentences.PROCEDURE_STEP_NEEDS_A_TITLE),

    /** A step of a procedure that went before the change reached it. */
    PROCEDURE_STEP_NOT_HERE_TO_CHANGE(17, HttpStatus.NOT_FOUND, Sentences.PROCEDURE_STEP_NOT_HERE_ON_WRITE),

    /** A step of a procedure that went before the deletion reached it. */
    PROCEDURE_STEP_NOT_HERE_TO_DELETE(18, HttpStatus.NOT_FOUND, Sentences.PROCEDURE_STEP_NOT_HERE_ON_WRITE),

    /** A step nobody was meant to tick off themselves, ticked off by somebody it was handed to. */
    PROCEDURE_STEP_NOT_YOURS_TO_TICK(20, HttpStatus.FORBIDDEN, "This step is ticked off by whoever runs the procedure"),

    /** A step ticked off by somebody the procedure was never handed to. */
    PROCEDURE_NOT_HANDED_TO_YOU(21, HttpStatus.FORBIDDEN, Sentences.PROCEDURE_NOT_YOURS),

    /** A step that waits on another one, or is ticked off already. */
    PROCEDURE_STEP_NOT_READY_TO_TICK(
            22,
            HttpStatus.BAD_REQUEST,
            "This step cannot be ticked off yet: a step it waits on is still open, or it is ticked off already"),

    /** A tick taken back by somebody who does not run the procedure. */
    PROCEDURE_STEP_NOT_YOURS_TO_UNTICK(
            23, HttpStatus.FORBIDDEN, "Only whoever runs the procedure can take a tick back"),

    /** A procedure that could not be read back after being changed, with the change already in. */
    PROCEDURE_NOT_HERE_AFTER_CHANGE(24, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A procedure that could not be read back after being marked done, with the marking already in. */
    PROCEDURE_NOT_HERE_AFTER_RESOLVING(25, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A procedure that could not be read back after being reopened, with the reopening already in. */
    PROCEDURE_NOT_HERE_AFTER_REOPENING(26, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A step named under a procedure it does not belong to. */
    PROCEDURE_STEP_NOT_IN_PROCEDURE(27, HttpStatus.NOT_FOUND, Sentences.PROCEDURE_STEP_NOT_HERE_ON_WRITE),

    /** A step named under a procedure template it does not belong to. */
    PROCEDURE_TEMPLATE_STEP_NOT_IN_TEMPLATE(28, HttpStatus.NOT_FOUND, Sentences.PROCEDURE_STEP_NOT_HERE_ON_WRITE),

    /** A managing party naming a group, tag or member that is not of the procedure's station. */
    PROCEDURE_PARTY_NOT_OF_STATION(
            30,
            HttpStatus.BAD_REQUEST,
            "The managing party names a group, tag or member of another station, so nothing was saved"),

    /** A managing party set without saying who it is. */
    PROCEDURE_PARTY_MISSING(31, HttpStatus.BAD_REQUEST, "Say who runs the procedure, so nothing was saved"),

    /** A procedure started from a template that is not one of the station's own. */
    PROCEDURE_TEMPLATE_NOT_HERE_TO_START(
            32, HttpStatus.NOT_FOUND, "That procedure template is not here, so no procedure was started"),

    /** A document step whose document template it may not generate. */
    PROCEDURE_STEP_DOCUMENT_UNSUITABLE(
            33,
            HttpStatus.BAD_REQUEST,
            "That document cannot be a step: it has to be in use, not meant for appointments, and ask the member"
                    + " or a guardian to sign, so nothing was saved"),

    /** A document step without a document template. */
    PROCEDURE_STEP_DOCUMENT_MISSING(
            34, HttpStatus.BAD_REQUEST, "Choose the document a document step asks to sign, so nothing was saved"),

    /** A step whose kind was changed after it already made its document. */
    PROCEDURE_STEP_DOCUMENT_ALREADY_MADE(
            35, HttpStatus.CONFLICT, "This step already made its document, so what it asks for stays as it is"),

    /** A document step set by somebody who may not generate documents for members. */
    PROCEDURE_STEP_DOCUMENT_NOT_YOURS(
            36,
            HttpStatus.FORBIDDEN,
            "Only those who generate documents for members can make a step ask to sign one, so nothing was saved"),

    /** A procedure made about somebody who is not a member of its station. */
    PROCEDURE_ABOUT_NOT_OF_STATION(
            37, HttpStatus.BAD_REQUEST, "That person is not a member here, so the procedure is not about them"),

    /** A document step on a procedure that is about nobody, whose document would be about nobody either. */
    PROCEDURE_STEP_DOCUMENT_NEEDS_ABOUT(
            38,
            HttpStatus.BAD_REQUEST,
            "Choose whom the procedure is about before a step asks to sign a document, so nothing was saved"),

    /** A procedure made about somebody without naming them. */
    PROCEDURE_ABOUT_MISSING(39, HttpStatus.BAD_REQUEST, "Choose whom the procedure is about, so nothing was changed"),

    /** A tick by hand on a step that its document's signatures settle. */
    PROCEDURE_STEP_SETTLED_BY_SIGNATURES(
            40,
            HttpStatus.CONFLICT,
            "This step is done once its document is signed, confirmed on paper or waived, so it is not ticked by hand"),

    /** Asking again for a document step that is ticked, locked, still waiting or not a document step. */
    PROCEDURE_STEP_NOT_TO_ASK_AGAIN(
            41,
            HttpStatus.CONFLICT,
            "This step does not wait to be asked again: it is done, locked, still waiting for signatures or asks for no document"),

    /** Asking again for a step's document by somebody who may not generate documents for members. */
    PROCEDURE_STEP_DOCUMENT_NOT_YOURS_TO_ASK(
            42, HttpStatus.FORBIDDEN, "Only those who generate documents for members can ask for a step's document"),

    /** Procedures for more members at once than one request starts. */
    PROCEDURE_TOO_MANY_AT_ONCE(
            43, HttpStatus.BAD_REQUEST, "That is more people than procedures are started for at once, so none was"),

    /** A note on a step written by somebody who does not run the procedure. */
    PROCEDURE_STEP_NOTE_NOT_YOURS(
            29, HttpStatus.FORBIDDEN, "Only whoever runs the procedure can write a note on a step"),

    /** A procedure nobody but the people it was handed to may open, read by somebody else. */
    PROCEDURE_NOT_PUBLIC(1, HttpStatus.FORBIDDEN, Sentences.PROCEDURE_NOT_YOURS),

    /** A procedure open to the station but not handed to this reader. */
    PROCEDURE_NOT_YOURS(2, HttpStatus.FORBIDDEN, Sentences.PROCEDURE_NOT_YOURS);

    private final Definition definition;

    ProcedureRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.PROCEDURES, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
