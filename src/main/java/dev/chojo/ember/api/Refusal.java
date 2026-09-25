/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.http.HttpStatus;

/**
 * Every refusal that has been given a name, with the sentence a reader is shown for it.
 *
 * <p>A reader who reports a failure can say what they were doing and what the screen said, and
 * neither of those finds the line that refused them. The constant's name travels in the error body
 * as {@code code}, so a report names the refusal exactly and an operator reading
 * {@code FORM_ANSWER_UNREADABLE} already knows most of what happened before looking at anything.
 *
 * <p>The name and the wording live here together on purpose. A code kept in one place and a
 * sentence written at the throw site drift apart within a release; here changing one means seeing
 * the other. The compiler guarantees the names are unique, so nothing has to allocate a number.
 *
 * <p>A refusal that has not been named yet keeps working exactly as it did: it throws its framework
 * exception, answers its status and its sentence, and simply carries no {@code code}. Naming them
 * is therefore an area at a time, not a flag day.
 */
public enum Refusal {
    /** A body arrived that is not JSON at all. */
    BODY_NOT_JSON(HttpStatus.BAD_REQUEST, "The request body is not valid JSON, so nothing was saved"),

    /** A body arrived carrying a field the endpoint has no place for. */
    BODY_UNEXPECTED_FIELD(HttpStatus.BAD_REQUEST, "The request body carries a field this endpoint does not accept"),

    /** A body arrived shaped differently from what the endpoint reads. */
    BODY_DOES_NOT_MATCH(HttpStatus.BAD_REQUEST, "The request body does not match what this endpoint expects"),

    /** A body arrived whose shape was right but whose contents were refused. */
    BODY_VALUE_REJECTED(HttpStatus.BAD_REQUEST, "A value in the request body is not one this endpoint accepts"),

    /** Something in the request could not be used, and whoever refused it said nothing readable. */
    INPUT_NOT_USABLE(HttpStatus.BAD_REQUEST, "Something in what was sent could not be used, so nothing was saved"),

    /** A second row with details that may only exist once. */
    ALREADY_EXISTS(HttpStatus.CONFLICT, "Something with the same details is already there, so nothing was saved"),

    /** A row naming something gone, or something still depended on. */
    STILL_LINKED(
            HttpStatus.CONFLICT,
            "This names something that is no longer there, or something else still depends on it, "
                    + "so nothing was saved"),

    /** A value larger, longer or otherwise different from what can be kept. */
    DOES_NOT_FIT(
            HttpStatus.BAD_REQUEST,
            "Something in what was sent does not fit what can be stored here, " + "so nothing was saved"),

    /** Two changes to the same thing at the same moment, one of which had to be undone. */
    CHANGE_COLLIDED(
            HttpStatus.CONFLICT,
            "Somebody changed the same thing at the same moment, so this change was undone. "
                    + "Trying again usually works"),

    /** Work that ran long enough to be stopped before it finished. */
    TOOK_TOO_LONG(
            HttpStatus.SERVICE_UNAVAILABLE,
            "This took too long and was stopped before anything was saved. Trying again may work"),

    /** The store Ember keeps its data in could not be reached. */
    STORE_UNREACHABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Ember could not reach the place it keeps its data, so nothing was saved. "
                    + "Trying again in a moment usually works"),

    /** Nobody expected this one, and the log is where it is explained. */
    UNEXPECTED_FAULT(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Something went wrong in Ember and the request was not carried out, so nothing was saved. "
                    + "Trying again may work; if it keeps happening, please report it"),

    /** An address that cannot name anything, because the identifier in it is not one. */
    ADDRESS_NOT_AN_IDENTIFIER(HttpStatus.NOT_FOUND, "That address does not name anything"),

    /** Gone, or another station's. The two are deliberately the same answer. */
    NOT_HERE_OR_NOT_YOURS(HttpStatus.NOT_FOUND, "That is not here any more, or it is not yours to open"),

    /** A form that has been deleted between opening the screen and acting on it. */
    FORM_NOT_HERE(HttpStatus.NOT_FOUND, "That form is not here any more, so nothing was changed"),

    /** An answer that was to be read back or changed and is not there. */
    FORM_ANSWER_NOT_HERE(HttpStatus.NOT_FOUND, "That answer is not here any more"),

    /** Answers that arrived in a shape nothing could be made of. */
    FORM_ANSWER_UNREADABLE(
            HttpStatus.BAD_REQUEST,
            "The answers could not be read, so nothing was saved. Fill the form in again and send it once more"),

    /** Answers that were read and then refused for what they said. */
    FORM_ANSWER_REFUSED(HttpStatus.BAD_REQUEST, "The answers could not be saved"),

    /** A form somebody has already answered once, where once is all it takes. */
    FORM_ALREADY_ANSWERED(HttpStatus.CONFLICT, "You have already answered this form"),

    /** A form that is closed, not yet open, or never published. */
    FORM_NOT_TAKING_ANSWERS(HttpStatus.GONE, "This form is not taking answers"),

    /** A link that no form is reached by, which covers a link that has since been replaced. */
    FORM_LINK_UNKNOWN(HttpStatus.NOT_FOUND, "No form is reached by this link"),

    /** A form that exists but is not one strangers may answer. */
    FORM_NOT_ANSWERED_FROM_OUTSIDE(HttpStatus.NOT_FOUND, "That form cannot be answered from outside the station"),

    /** Answering the same public form far more often than a person could. */
    FORM_ANSWERED_TOO_OFTEN(
            HttpStatus.TOO_MANY_REQUESTS, "This form has been answered too often from here. Try again shortly"),

    /** A station that is not here, asked for by an address that names one. */
    STATION_NOT_HERE(HttpStatus.NOT_FOUND, "That station is not here"),

    /**
     * A public address another station on this instance already answers at. Named because a screen
     * has to tell this one refusal apart from every other refusal a settings form can get, and
     * matching on the English sentence to do it broke the moment anybody reworded it.
     */
    STATION_SLUG_TAKEN(HttpStatus.CONFLICT, "Another station is already reached at that address"),

    /** A page asked for by an address nothing sits at. */
    PAGE_NOT_HERE(HttpStatus.NOT_FOUND, "That page is not here any more"),

    /** A link that no page is reached by, which covers a link that has since been replaced. */
    PAGE_LINK_UNKNOWN(HttpStatus.NOT_FOUND, "No page is reached by this link"),

    /** A page whose contents were read and then refused for what they said. */
    PAGE_NOT_SAVED(HttpStatus.BAD_REQUEST, "The page could not be saved"),

    /** A cell on a page pointing at a form that is gone or is of the wrong kind. */
    PAGE_FORM_NOT_HERE(HttpStatus.NOT_FOUND, "The form this part of the page shows is not here any more"),

    /** A file in the media library that is gone. */
    FILE_NOT_HERE(HttpStatus.NOT_FOUND, "That file is not here any more"),

    /** A folder in the media library that is gone. */
    FOLDER_NOT_HERE(HttpStatus.NOT_FOUND, "That folder is not here any more"),

    /** A tag on a file that is gone. */
    FILE_TAG_NOT_HERE(HttpStatus.NOT_FOUND, "That tag is not here any more"),

    /** A file bigger than this instance takes. */
    UPLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "That file is larger than this instance accepts"),

    /** Work on a station's own things, asked for by somebody who has not said which station. */
    NO_STATION_CHOSEN(HttpStatus.BAD_REQUEST, "Choose a station before doing this"),

    /** An upload that arrived without the file itself. */
    UPLOAD_MISSING_FILE(HttpStatus.BAD_REQUEST, "The upload arrived without a file in it"),

    /** A picture that could not be read as one, or is of a kind not taken here. */
    UPLOAD_NOT_A_PICTURE(HttpStatus.BAD_REQUEST, "That file could not be taken as a picture"),

    /** An upload that was read and then refused for what it held. */
    UPLOAD_NOT_SAVED(HttpStatus.BAD_REQUEST, "The upload could not be saved"),

    /** An upload that broke on the way in, which is Ember's to look into rather than the reader's. */
    UPLOAD_NOT_PROCESSED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The upload could not be worked through, so nothing was saved. Trying again may work"),

    /** A picture that is not kept, asked for by an address that names one. */
    PICTURE_NOT_HERE(HttpStatus.NOT_FOUND, "That picture is not here"),

    /** A session that is signed in to a station but stands for no account. */
    SESSION_HAS_NO_ACCOUNT(HttpStatus.BAD_REQUEST, "This session does not stand for an account"),

    /** A profile question that is gone, or belongs to another station. */
    PROFILE_FIELD_NOT_HERE(HttpStatus.NOT_FOUND, "That profile question is not here any more"),

    /** A member record that is gone, or belongs to another station. */
    MEMBER_NOT_HERE(HttpStatus.NOT_FOUND, "That member is not here any more"),

    /** An account that is gone, asked for by an address or a body that names one. */
    ACCOUNT_NOT_HERE(HttpStatus.NOT_FOUND, "That account is not here any more"),

    /** A registration for an appointment that is gone. */
    EVENT_REGISTRATION_NOT_HERE(HttpStatus.NOT_FOUND, "That registration is not here any more"),

    /** An appointment that is gone, or was never this station's. */
    EVENT_NOT_HERE(HttpStatus.NOT_FOUND, "That appointment is not here any more"),

    /** A waiting list that is gone, or is not open to the public. */
    WAITING_LIST_NOT_HERE(HttpStatus.NOT_FOUND, "That waiting list is not here any more"),

    /** Signing up for the same waiting list far more often than a person could. */
    WAITING_LIST_TOO_OFTEN(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts from here. Try again shortly"),

    /** An entry on a waiting list that is gone. */
    WAITING_LIST_ENTRY_NOT_HERE(HttpStatus.NOT_FOUND, "That entry is not here any more"),

    /** A question on a waiting list's form that is gone. */
    WAITING_LIST_FIELD_NOT_HERE(HttpStatus.NOT_FOUND, "That question is not on this waiting list any more"),

    /** An invite code no waiting list is reached by. */
    WAITING_LIST_INVITE_UNKNOWN(HttpStatus.NOT_FOUND, "No waiting list is reached by this invite"),

    /** An application to join a station that is gone or already answered. */
    STATION_APPLICATION_NOT_HERE(HttpStatus.NOT_FOUND, "That application is not here any more"),

    /** A confirmation link for an application that has been used, has run out, or was never one. */
    STATION_APPLICATION_LINK_UNKNOWN(HttpStatus.NOT_FOUND, "That confirmation link is no longer good"),

    /** Asking to set an instance up far more often than a person could. */
    SETUP_TOO_OFTEN(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts from here. Try again shortly"),

    /** A document that is gone, or is not this reader's to see. */
    DOCUMENT_NOT_HERE(HttpStatus.NOT_FOUND, "That document is not here any more"),

    /** Documents at all, at a station that has switched them off. */
    DOCUMENTS_SWITCHED_OFF(HttpStatus.NOT_FOUND, "This station does not keep documents"),

    /** A document somebody else's, in a station that does not let this reader read it. */
    DOCUMENT_NOT_YOURS(HttpStatus.FORBIDDEN, "That document belongs to somebody you do not answer for"),

    /** A change to a document by somebody who may read it but not write it. */
    DOCUMENT_NOT_YOURS_TO_CHANGE(HttpStatus.FORBIDDEN, "You may not change this document"),

    /** A document filed against a member by somebody who may not file one. */
    DOCUMENT_NOT_YOURS_TO_ADD(HttpStatus.FORBIDDEN, "You may not add a document for this member"),

    /** A hidden document being written by somebody who may not hide things. */
    DOCUMENT_HIDING_NOT_ALLOWED(HttpStatus.FORBIDDEN, "You may not mark a document as hidden"),

    /**
     * A peer that could not be reached, which is answered as a bad request rather than as a bad
     * gateway on purpose: the address is the thing the operator just typed, and a {@code 5xx} would
     * be read as Ember having fallen over when what happened is that an address somewhere else did
     * not work. The sentence names which of the several ways it did not work.
     */
    PEER_DID_NOT_ANSWER(HttpStatus.BAD_REQUEST, "That instance could not be reached"),

    /** A catalog of questions that is gone, or belongs to another station. */
    QUIZ_CATALOG_NOT_HERE(HttpStatus.NOT_FOUND, "That catalog is not here any more"),

    /** A grouping of questions that is gone. */
    QUIZ_CATEGORY_NOT_HERE(HttpStatus.NOT_FOUND, "That category is not here any more"),

    /** A test that is gone, or belongs to another station. */
    QUIZ_TEST_NOT_HERE(HttpStatus.NOT_FOUND, "That test is not here any more"),

    /** An example file asked for in a format there is no example in. */
    QUIZ_TEMPLATE_FORMAT_UNKNOWN(HttpStatus.NOT_FOUND, "There is no example file in that format"),

    /** A section of a protocol that is gone, or belongs to another station. */
    PROTOCOL_SECTION_NOT_HERE(HttpStatus.NOT_FOUND, "That section is not here any more"),

    /** A point in a protocol that is gone, or belongs to another station. */
    PROTOCOL_ITEM_NOT_HERE(HttpStatus.NOT_FOUND, "That point is not here any more"),

    /** A test attempt that belongs to somebody else. */
    QUIZ_ATTEMPT_NOT_YOURS(HttpStatus.FORBIDDEN, "That attempt is somebody else's"),

    /** A test attempt that is gone. */
    QUIZ_ATTEMPT_NOT_HERE(HttpStatus.NOT_FOUND, "That attempt is not here any more"),

    /** An answer written on a paper that is no longer being sat. */
    QUIZ_ALREADY_HANDED_IN(
            HttpStatus.CONFLICT,
            "This paper has already been handed in, so the answer was not saved. "
                    + "Reload the page to see it as it stands"),

    /** A paper handed in that was not still being written, whether already in or out of time. */
    QUIZ_NOT_HANDED_IN(
            HttpStatus.CONFLICT,
            "This paper could not be handed in: it is already in, or the time for it has run out. "
                    + "Reload the page to see where it stands"),

    /** A procedure open only to the people it was handed to. */
    PROCEDURE_NOT_YOURS(HttpStatus.FORBIDDEN, "That procedure was not handed to you");

    private final HttpStatus status;
    private final String message;

    Refusal(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * The status this refusal answers with, chosen so the reader is told truthfully whose problem
     * it is: what they sent is a {@code 400}, what they may not do is a {@code 403}, what is not
     * there is a {@code 404}, a collision is a {@code 409}, and only a fault is a {@code 500}.
     *
     * @return the HTTP status
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * The sentence the reader is shown.
     *
     * @return one sentence saying what was refused and what became of their data
     */
    public String message() {
        return message;
    }

    /**
     * The exception to throw for this refusal.
     *
     * @return an exception carrying this refusal's status, sentence and name
     */
    public RefusalResponse raise() {
        return new RefusalResponse(this, message);
    }

    /**
     * The exception to throw for this refusal, naming the particular thing it was about.
     *
     * <p>The detail is the caller's own words back at them, a field name they sent or a value they
     * chose, never anything of Ember's.
     *
     * @param detail what the refusal was about, in the reader's terms
     * @return an exception carrying this refusal's status and name, and its sentence with the
     *         detail named
     */
    public RefusalResponse raise(String detail) {
        return new RefusalResponse(this, message + ": " + detail);
    }
}
