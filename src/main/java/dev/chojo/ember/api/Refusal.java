/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.http.HttpStatus;

/**
 * Every refusal that has been given a code, with the sentence a reader is shown for it.
 *
 * <p>A reader who reports a failure can say what they were doing and what the screen said, and
 * neither of those finds the line that refused them. The code travels in the error body as
 * {@code code} and is short enough to be read out over a telephone, so a report arrives naming one
 * line rather than a kind of refusal thrown from eleven.
 *
 * <p>The code is a letter for the area, a hyphen and a number: {@code F-021}. The letters are the
 * registry in {@link Area}, which is the one place a feature claims one, so two features cannot
 * both be {@code F}. The number is this constant's own and belongs to nothing else, ever: see
 * {@link RetiredRefusals} for what happens to the number of a constant that is deleted.
 *
 * <p>One constant stands for one throw site. That is what makes the code worth quoting. The
 * exception is a pair of lines that deliberately answer alike, a lookup that missed and the
 * ownership check right behind it: handing those two codes would tell a caller that the thing
 * exists somewhere else, which is the one thing the shared answer is there to withhold. Those
 * share a constant and say so in their own words below.
 *
 * <p>The code and the wording live here together on purpose. A code kept in one place and a
 * sentence written at the throw site drift apart within a release; here changing one means seeing
 * the other. Where several refusals say the same thing, they name the same {@code Sentences}
 * constant rather than repeating it, so the prose has a single home.
 */
public enum Refusal {
    /** A peer that answered as a peer would but named no key to recognise it by. */
    PEER_NAMED_NO_KEY(Area.ADMIN, 1, HttpStatus.BAD_REQUEST, Sentences.PEER_DID_NOT_ANSWER),

    /**
     * A peer whose discovery card could not be fetched at all. Named because a screen has to tell
     * this one refusal apart from every other refusal the peer form can get.
     */
    PEER_DID_NOT_ANSWER(Area.ADMIN, 2, HttpStatus.BAD_REQUEST, Sentences.PEER_DID_NOT_ANSWER),

    /** A body arrived that is not JSON at all. */
    BODY_NOT_JSON(Area.BODY, 1, HttpStatus.BAD_REQUEST, "The request body is not valid JSON, so nothing was saved"),

    /** A body arrived carrying a field the endpoint has no place for. */
    BODY_UNEXPECTED_FIELD(
            Area.BODY, 2, HttpStatus.BAD_REQUEST, "The request body carries a field this endpoint does not accept"),

    /** A body arrived shaped differently from what the endpoint reads. */
    BODY_DOES_NOT_MATCH(
            Area.BODY, 3, HttpStatus.BAD_REQUEST, "The request body does not match what this endpoint expects"),

    /** A body arrived whose shape was right but whose contents were refused. */
    BODY_VALUE_REJECTED(
            Area.BODY, 4, HttpStatus.BAD_REQUEST, "A value in the request body is not one this endpoint accepts"),

    /** Something in the request could not be used, and whoever refused it said nothing readable. */
    INPUT_NOT_USABLE(
            Area.BODY, 5, HttpStatus.BAD_REQUEST, "Something in what was sent could not be used, so nothing was saved"),

    /** Documents at all, at a station that has switched them off. */
    DOCUMENTS_SWITCHED_OFF(Area.DOCUMENTS, 1, HttpStatus.NOT_FOUND, "This station does not keep documents"),

    /** The documents of a member the reader neither manages nor answers for. */
    DOCUMENT_LIST_NOT_YOURS(Area.DOCUMENTS, 2, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS),

    /** A hidden document being written by somebody who may not hide things. */
    DOCUMENT_HIDING_NOT_ALLOWED(Area.DOCUMENTS, 3, HttpStatus.FORBIDDEN, "You may not mark a document as hidden"),

    /** Work on a station's documents, asked for by somebody who has not said which station. */
    NO_STATION_CHOSEN_FOR_DOCUMENTS(Area.DOCUMENTS, 4, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /** An upload naming members, by somebody who may not file documents against one. */
    DOCUMENT_MEMBERS_NOT_YOURS_TO_NAME(Area.DOCUMENTS, 5, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS_TO_ADD),

    /** A document whose stored file is gone. */
    DOCUMENT_CONTENT_NOT_HERE(Area.DOCUMENTS, 6, HttpStatus.NOT_FOUND, Sentences.DOCUMENT_NOT_HERE),

    /** A document whose tile picture could not be had. */
    DOCUMENT_THUMBNAIL_NOT_HERE(Area.DOCUMENTS, 7, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** A document marked hidden, read by somebody who may not see hidden ones. */
    DOCUMENT_HIDDEN_FROM_YOU(Area.DOCUMENTS, 8, HttpStatus.NOT_FOUND, Sentences.DOCUMENT_NOT_HERE),

    /** A document of somebody the reader does not answer for. */
    DOCUMENT_NOT_YOURS_TO_READ(Area.DOCUMENTS, 9, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS),

    /** A change to a document by somebody who may read it but not write it. */
    DOCUMENT_NOT_YOURS_TO_CHANGE(Area.DOCUMENTS, 10, HttpStatus.FORBIDDEN, "You may not change this document"),

    /** A document filed against a member by somebody who may not file one. */
    DOCUMENT_NOT_YOURS_TO_ADD(Area.DOCUMENTS, 11, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS_TO_ADD),

    /** A mailbox rule filing attachments against members, written by somebody who may not file one. */
    DOCUMENT_NOT_YOURS_TO_FILE(Area.DOCUMENTS, 12, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS_TO_ADD),

    /** A registration whose answers were to be changed and is gone. */
    EVENT_REGISTRATION_NOT_HERE_ON_FIELD_CHANGE(
            Area.EVENTS, 1, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** The station a registration spreadsheet is headed with, gone between the two reads. */
    STATION_NOT_HERE_FOR_REGISTRATION_CSV(Area.EVENTS, 2, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** The station a registration sheet is headed with, gone between the two reads. */
    STATION_NOT_HERE_FOR_REGISTRATION_SHEET(Area.EVENTS, 3, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** The station a registration table is drawn for, gone between the two reads. */
    STATION_NOT_HERE_FOR_REGISTRATION_TABLE(Area.EVENTS, 4, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A registration whose status was to be set and is gone. */
    EVENT_REGISTRATION_NOT_HERE_ON_STATUS_CHANGE(
            Area.EVENTS, 5, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration that went between being read and having its status written. */
    EVENT_REGISTRATION_STATUS_NOT_CHANGED(Area.EVENTS, 6, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration being answered for that is gone. */
    EVENT_REGISTRATION_NOT_HERE_ON_ANSWER(Area.EVENTS, 7, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration that could not be marked as not coming. */
    EVENT_REGISTRATION_NOT_REFUSED(Area.EVENTS, 8, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration that could not be marked as coming. */
    EVENT_REGISTRATION_NOT_CONFIRMED(Area.EVENTS, 9, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration being taken back that is gone. */
    EVENT_REGISTRATION_NOT_HERE_ON_WITHDRAWAL(
            Area.EVENTS, 10, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration that went between being read and being withdrawn. */
    EVENT_REGISTRATION_NOT_WITHDRAWN(Area.EVENTS, 11, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A withdrawal being undone whose registration is gone. */
    EVENT_REGISTRATION_NOT_HERE_ON_UNDO(Area.EVENTS, 12, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** An appointment that went between the screen being opened and the change being saved. */
    EVENT_NOT_HERE_ON_CHANGE(Area.EVENTS, 13, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** An appointment that was already gone when its deletion was asked for. */
    EVENT_NOT_HERE_ON_DELETE(Area.EVENTS, 14, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** An appointment that was already gone when its cancellation was asked for. */
    EVENT_NOT_HERE_ON_CANCELLATION(Area.EVENTS, 15, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /**
     * A form asked for by a route that then checks whose it is. The ownership check behind it
     * answers {@link #NOT_YOURS_TO_OPEN}, which is the shared answer for a thing that is somebody
     * else's.
     */
    FORM_NOT_HERE(Area.FORMS, 1, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form that went between an act on it succeeding and the answer being drawn. */
    FORM_NOT_HERE_ON_REREAD(Area.FORMS, 2, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form that went between the screen being opened and the change being saved. */
    FORM_NOT_HERE_ON_CHANGE(Area.FORMS, 3, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form that was already gone when its deletion was asked for. */
    FORM_NOT_HERE_ON_DELETE(Area.FORMS, 4, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form that went before how far it reaches could be written. */
    FORM_NOT_HERE_ON_VISIBILITY_CHANGE(Area.FORMS, 5, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form that went before it could be closed. */
    FORM_NOT_HERE_ON_CLOSE(Area.FORMS, 6, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** The station an export of answers is headed with, gone between the two reads. */
    STATION_NOT_HERE_FOR_FORM_EXPORT(Area.FORMS, 7, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** The station a shared form belongs to, asked for to draw its name and colours. */
    STATION_NOT_HERE_BEHIND_FORM_LINK(Area.FORMS, 8, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A link that no form is reached by, which covers a link that has since been replaced. */
    FORM_LINK_UNKNOWN(Area.FORMS, 9, HttpStatus.NOT_FOUND, "No form is reached by this link"),

    /** A form that is closed, not yet open, or never published. */
    FORM_NOT_TAKING_ANSWERS(Area.FORMS, 10, HttpStatus.GONE, "This form is not taking answers"),

    /** Answering the same public form far more often than a person could. */
    FORM_ANSWERED_TOO_OFTEN(
            Area.FORMS,
            11,
            HttpStatus.TOO_MANY_REQUESTS,
            "This form has been answered too often from here. Try again shortly"),

    /** A form somebody has already answered once, where once is all it takes. */
    FORM_ALREADY_ANSWERED(Area.FORMS, 12, HttpStatus.CONFLICT, "You have already answered this form"),

    /** Answers that were read and then refused for what they said. */
    FORM_ANSWER_REFUSED(Area.FORMS, 13, HttpStatus.BAD_REQUEST, "The answers could not be saved"),

    /** Answers that arrived in a shape nothing could be made of. */
    FORM_ANSWER_UNREADABLE(
            Area.FORMS,
            14,
            HttpStatus.BAD_REQUEST,
            "The answers could not be read, so nothing was saved. Fill the form in again and send it once more"),

    /** The station a public form address names, which is not here. */
    STATION_NOT_HERE_BEHIND_PUBLIC_FORM(Area.FORMS, 15, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /**
     * A public form that is gone, or belongs to a station other than the one in the address. The
     * two are one code deliberately: telling them apart would say that the form exists elsewhere.
     */
    PUBLIC_FORM_NOT_HERE(Area.FORMS, 16, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form of a kind strangers may not answer. */
    FORM_NOT_ANSWERED_FROM_OUTSIDE(Area.FORMS, 17, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_ANSWERED_FROM_OUTSIDE),

    /** A form that reaches less far than an open address needs it to. */
    FORM_NOT_OPENLY_ADDRESSED(Area.FORMS, 18, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_ANSWERED_FROM_OUTSIDE),

    /** A cell on a page pointing at a form that is gone or is of the wrong kind. */
    PAGE_FORM_NOT_HERE(
            Area.FORMS, 19, HttpStatus.NOT_FOUND, "The form this part of the page shows is not here any more"),

    /**
     * An answer that was to be acknowledged and is gone, or belongs to another form. The two are
     * one code deliberately: telling them apart would say that the answer exists elsewhere.
     */
    FORM_ANSWER_NOT_HERE(Area.FORMS, 20, HttpStatus.NOT_FOUND, "That answer is not here any more"),

    /** Nobody expected this one, and the log is where it is explained. */
    UNEXPECTED_FAULT(Area.GENERAL, 1, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UNEXPECTED_FAULT),

    /** The store refused in a way nothing here has a name for, which is ours to look into. */
    UNEXPECTED_FAULT_FROM_UNKNOWN_STATE(Area.GENERAL, 2, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UNEXPECTED_FAULT),

    /** A second row with details that may only exist once. */
    ALREADY_EXISTS(
            Area.GENERAL,
            3,
            HttpStatus.CONFLICT,
            "Something with the same details is already there, so nothing was saved"),

    /** A row naming something gone, or something still depended on. */
    STILL_LINKED(
            Area.GENERAL,
            4,
            HttpStatus.CONFLICT,
            "This names something that is no longer there, or something else still depends on it, "
                    + "so nothing was saved"),

    /** A value larger, longer or otherwise different from what can be kept. */
    DOES_NOT_FIT(
            Area.GENERAL,
            5,
            HttpStatus.BAD_REQUEST,
            "Something in what was sent does not fit what can be stored here, so nothing was saved"),

    /** Two changes to the same thing at the same moment, one of which had to be undone. */
    CHANGE_COLLIDED(
            Area.GENERAL,
            6,
            HttpStatus.CONFLICT,
            "Somebody changed the same thing at the same moment, so this change was undone. "
                    + "Trying again usually works"),

    /** Work that ran long enough to be stopped before it finished. */
    TOOK_TOO_LONG(
            Area.GENERAL,
            7,
            HttpStatus.SERVICE_UNAVAILABLE,
            "This took too long and was stopped before anything was saved. Trying again may work"),

    /** The store Ember keeps its data in could not be reached. */
    STORE_UNREACHABLE(
            Area.GENERAL,
            8,
            HttpStatus.SERVICE_UNAVAILABLE,
            "Ember could not reach the place it keeps its data, so nothing was saved. "
                    + "Trying again in a moment usually works"),

    /** An address that cannot name anything, because the identifier in it is not one. */
    ADDRESS_NOT_AN_IDENTIFIER(Area.GENERAL, 9, HttpStatus.NOT_FOUND, "That address does not name anything"),

    /** Something already loaded that turns out to belong to another station. */
    NOT_YOURS_TO_OPEN(Area.GENERAL, 10, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /**
     * Gone, or another station's, from the loader that does both at once. The two are one code
     * deliberately: telling them apart would say that the thing exists at another station, which
     * is what the shared answer is there to withhold.
     */
    NOT_HERE_OR_NOT_YOURS(Area.GENERAL, 11, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /**
     * A file in the media library that is gone, or one held back from this reader. The two are one
     * code deliberately: telling them apart would say that the file is there and withheld.
     */
    FILE_NOT_HERE(Area.MEDIA_LIBRARY, 1, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /**
     * A picture in the media library that is gone, or one held back from this reader. One code for
     * the same reason as the file above.
     */
    PICTURE_NOT_HERE(Area.MEDIA_LIBRARY, 2, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** The library of a station, asked for by somebody who has not said which station. */
    NO_STATION_CHOSEN_FOR_LIBRARY(Area.MEDIA_LIBRARY, 3, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /** An upload to the library that arrived without the file itself. */
    UPLOAD_MISSING_FILE(Area.MEDIA_LIBRARY, 4, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A file bigger than this instance takes. */
    UPLOAD_TOO_LARGE(
            Area.MEDIA_LIBRARY, 5, HttpStatus.CONTENT_TOO_LARGE, "That file is larger than this instance accepts"),

    /** An upload that was read and then refused for what it held. */
    UPLOAD_NOT_SAVED(Area.MEDIA_LIBRARY, 6, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_NOT_SAVED),

    /** An upload that broke on the way in, which is Ember's to look into rather than the reader's. */
    UPLOAD_NOT_PROCESSED(Area.MEDIA_LIBRARY, 7, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UPLOAD_NOT_PROCESSED),

    /** A file that went between being read and being deleted. */
    FILE_NOT_DELETED(Area.MEDIA_LIBRARY, 8, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file whose description or alternative text could not be written. */
    FILE_NOT_HERE_ON_DETAIL_CHANGE(Area.MEDIA_LIBRARY, 9, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file that could not be moved into another folder. */
    FILE_NOT_HERE_ON_MOVE(Area.MEDIA_LIBRARY, 10, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A folder that went before its details could be written. */
    FOLDER_NOT_HERE_ON_CHANGE(Area.MEDIA_LIBRARY, 11, HttpStatus.NOT_FOUND, Sentences.FOLDER_NOT_HERE),

    /** A folder that was already gone when its deletion was asked for. */
    FOLDER_NOT_HERE_ON_DELETE(Area.MEDIA_LIBRARY, 12, HttpStatus.NOT_FOUND, Sentences.FOLDER_NOT_HERE),

    /** A tag that went before its name or colour could be written. */
    FILE_TAG_NOT_HERE_ON_CHANGE(Area.MEDIA_LIBRARY, 13, HttpStatus.NOT_FOUND, Sentences.FILE_TAG_NOT_HERE),

    /** A tag that was already gone when its deletion was asked for. */
    FILE_TAG_NOT_HERE_ON_DELETE(Area.MEDIA_LIBRARY, 14, HttpStatus.NOT_FOUND, Sentences.FILE_TAG_NOT_HERE),

    /** A tag that could not be put on a file. */
    FILE_TAG_NOT_HERE_ON_ASSIGNMENT(Area.MEDIA_LIBRARY, 15, HttpStatus.NOT_FOUND, Sentences.FILE_TAG_NOT_HERE),

    /** A tag that could not be taken off a file. */
    FILE_TAG_NOT_HERE_ON_REMOVAL(Area.MEDIA_LIBRARY, 16, HttpStatus.NOT_FOUND, Sentences.FILE_TAG_NOT_HERE),

    /** An avatar asked for by somebody who may not see it, or of somebody who has none. */
    AVATAR_NOT_HERE(Area.MEMBERS, 1, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** An avatar upload that arrived without the picture itself. */
    AVATAR_UPLOAD_MISSING_FILE(Area.MEMBERS, 2, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A picture that could not be read as one, or is of a kind not taken here. */
    AVATAR_NOT_A_PICTURE(Area.MEMBERS, 3, HttpStatus.BAD_REQUEST, "That file could not be taken as a picture"),

    /** An avatar that was read and then refused for what it held. */
    AVATAR_NOT_SAVED(Area.MEMBERS, 4, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_NOT_SAVED),

    /** An avatar that broke on the way in, which is Ember's to look into rather than the reader's. */
    AVATAR_NOT_PROCESSED(Area.MEMBERS, 5, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UPLOAD_NOT_PROCESSED),

    /** A session that is signed in to a station but stands for no account. */
    SESSION_HAS_NO_ACCOUNT(Area.MEMBERS, 6, HttpStatus.BAD_REQUEST, Sentences.SESSION_HAS_NO_ACCOUNT),

    /** A session whose account could not be resolved to the one an avatar is kept under. */
    SESSION_ACCOUNT_NOT_RESOLVED(Area.MEMBERS, 7, HttpStatus.BAD_REQUEST, Sentences.SESSION_HAS_NO_ACCOUNT),

    /** An account that is not a member of the station the reader is signed in to. */
    MEMBER_NOT_HERE(Area.MEMBERS, 8, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** An account being sent through joining again that is gone. */
    ACCOUNT_NOT_HERE_ON_ONBOARDING_AGAIN(Area.MEMBERS, 9, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** An account being given a passkey code that is gone. */
    ACCOUNT_NOT_HERE_ON_PASSKEY_CODE(Area.MEMBERS, 10, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** An account whose details were to be changed and is gone. */
    ACCOUNT_NOT_HERE_ON_CHANGE(Area.MEMBERS, 11, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** An account that went between being read and having its details written. */
    MEMBER_NOT_HERE_ON_CHANGE(Area.MEMBERS, 12, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** An account whose password was to be reset and is gone. */
    ACCOUNT_NOT_HERE_ON_PASSWORD_RESET(Area.MEMBERS, 13, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** An account the password reset mail could not be sent for. */
    ACCOUNT_NOT_HERE_ON_PASSWORD_RESET_MAIL(Area.MEMBERS, 14, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** Profile questions of a station, asked for by somebody who has not said which station. */
    NO_STATION_CHOSEN_FOR_PROFILE_QUESTIONS(Area.MEMBERS, 15, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /** A profile question that went before the change to it could be written. */
    PROFILE_FIELD_NOT_HERE_ON_CHANGE(Area.MEMBERS, 16, HttpStatus.NOT_FOUND, Sentences.PROFILE_FIELD_NOT_HERE),

    /** A profile question that was already gone when its deletion was asked for. */
    PROFILE_FIELD_NOT_HERE_ON_DELETE(Area.MEMBERS, 17, HttpStatus.NOT_FOUND, Sentences.PROFILE_FIELD_NOT_HERE),

    /** Asking to set an instance up far more often than a person could. */
    SETUP_TOO_OFTEN(Area.INSTALLATION, 1, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A page that went between the editor being opened and the save being asked for. */
    PAGE_NOT_HERE_ON_SAVE(Area.PAGES, 1, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A page whose contents were read and then refused for what they said. */
    PAGE_NOT_SAVED(Area.PAGES, 2, HttpStatus.BAD_REQUEST, "The page could not be saved"),

    /** A page that went before a copy of it could be made. */
    PAGE_NOT_HERE_ON_COPY(Area.PAGES, 3, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A page that was already gone when its deletion was asked for. */
    PAGE_NOT_HERE_ON_DELETE(Area.PAGES, 4, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A page that went before how far it reaches could be written. */
    PAGE_NOT_HERE_ON_VISIBILITY_CHANGE(Area.PAGES, 5, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A public address no page of that station sits at. */
    PUBLIC_PAGE_NOT_HERE(Area.PAGES, 6, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A public page that exists but has nothing drawn to serve. */
    PUBLIC_PAGE_NOT_RENDERED(Area.PAGES, 7, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A station whose public site has no page to open on. */
    PUBLIC_LANDING_PAGE_NOT_HERE(Area.PAGES, 8, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** Public pages at all, at a station that has switched them off. */
    PUBLIC_PAGES_SWITCHED_OFF(Area.PAGES, 9, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A station that is not here, asked for by a public page address. */
    STATION_NOT_HERE_BEHIND_PUBLIC_PAGE(Area.PAGES, 10, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /**
     * A share link that leads nowhere, whether because no page answers to it or because the station
     * behind it has closed its public pages. One code deliberately: the reader holds a link and is
     * owed nothing about which of the two it was.
     */
    PAGE_LINK_UNKNOWN(Area.PAGES, 11, HttpStatus.NOT_FOUND, "No page is reached by this link"),

    /** An answer written on a paper that is no longer being sat. */
    QUIZ_ALREADY_HANDED_IN(
            Area.QUIZZES,
            1,
            HttpStatus.CONFLICT,
            "This paper has already been handed in, so the answer was not saved. "
                    + "Reload the page to see it as it stands"),

    /** A paper handed in that was not still being written, whether already in or out of time. */
    QUIZ_NOT_HANDED_IN(
            Area.QUIZZES,
            2,
            HttpStatus.CONFLICT,
            "This paper could not be handed in: it is already in, or the time for it has run out. "
                    + "Reload the page to see where it stands"),

    /** A paper that went between being handed in and being read back. */
    QUIZ_ATTEMPT_NOT_HERE_AFTER_HANDING_IN(Area.QUIZZES, 3, HttpStatus.NOT_FOUND, Sentences.QUIZ_ATTEMPT_NOT_HERE),

    /** A question that is gone, or belongs to another station's catalog. */
    QUIZ_QUESTION_NOT_HERE(Area.QUIZZES, 4, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** An attempt that is gone, asked for by a route that then checks whose test it was. */
    QUIZ_ATTEMPT_NOT_HERE(Area.QUIZZES, 5, HttpStatus.NOT_FOUND, Sentences.QUIZ_ATTEMPT_NOT_HERE),

    /** An attempt that is gone, asked for by the member sitting it. */
    QUIZ_ATTEMPT_NOT_HERE_FOR_MEMBER(Area.QUIZZES, 6, HttpStatus.NOT_FOUND, Sentences.QUIZ_ATTEMPT_NOT_HERE),

    /** A test attempt that belongs to somebody else. */
    QUIZ_ATTEMPT_NOT_YOURS(Area.QUIZZES, 7, HttpStatus.FORBIDDEN, "That attempt is somebody else's"),

    /** A catalog that went between the list being drawn and it being opened. */
    QUIZ_CATALOG_NOT_HERE(Area.QUIZZES, 8, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATALOG_NOT_HERE),

    /** A catalog that went before the change to it could be written. */
    QUIZ_CATALOG_NOT_CHANGED(Area.QUIZZES, 9, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATALOG_NOT_HERE),

    /** A catalog that went between being changed and being read back. */
    QUIZ_CATALOG_NOT_HERE_AFTER_CHANGE(Area.QUIZZES, 10, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATALOG_NOT_HERE),

    /** A catalog that was already gone when its deletion was asked for. */
    QUIZ_CATALOG_NOT_DELETED(Area.QUIZZES, 11, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATALOG_NOT_HERE),

    /** A grouping of questions that went before the change to it could be written. */
    QUIZ_CATEGORY_NOT_CHANGED(Area.QUIZZES, 12, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATEGORY_NOT_HERE),

    /** A grouping of questions that was already gone when its deletion was asked for. */
    QUIZ_CATEGORY_NOT_DELETED(Area.QUIZZES, 13, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATEGORY_NOT_HERE),

    /** An example file asked for in a format there is no example in. */
    QUIZ_TEMPLATE_FORMAT_UNKNOWN(Area.QUIZZES, 14, HttpStatus.NOT_FOUND, "There is no example file in that format"),

    /** A test that went before the change to it could be written. */
    QUIZ_TEST_NOT_CHANGED(Area.QUIZZES, 15, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A test that went between being changed and being read back. */
    QUIZ_TEST_NOT_HERE_AFTER_CHANGE(Area.QUIZZES, 16, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A test that was already gone when its deletion was asked for. */
    QUIZ_TEST_NOT_DELETED(Area.QUIZZES, 17, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A test that went between being started and being read back. */
    QUIZ_TEST_NOT_HERE_AFTER_ACTIVATION(Area.QUIZZES, 18, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A test that went before it could be closed. */
    QUIZ_TEST_NOT_CLOSED(Area.QUIZZES, 19, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A test that went between being closed and being read back. */
    QUIZ_TEST_NOT_HERE_AFTER_CLOSING(Area.QUIZZES, 20, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A procedure nobody but the people it was handed to may open, read by somebody else. */
    PROCEDURE_NOT_PUBLIC(Area.PROCEDURES, 1, HttpStatus.FORBIDDEN, Sentences.PROCEDURE_NOT_YOURS),

    /** A procedure open to the station but not handed to this reader. */
    PROCEDURE_NOT_YOURS(Area.PROCEDURES, 2, HttpStatus.FORBIDDEN, Sentences.PROCEDURE_NOT_YOURS),

    /** A confirmation link for an application that has been used, has run out, or was never one. */
    STATION_APPLICATION_LINK_UNKNOWN(
            Area.STATIONS, 1, HttpStatus.NOT_FOUND, "That confirmation link is no longer good"),

    /** An application to join a station that is gone. */
    STATION_APPLICATION_NOT_HERE(Area.STATIONS, 2, HttpStatus.NOT_FOUND, Sentences.STATION_APPLICATION_NOT_HERE),

    /** An application that was gone or already answered when its acceptance was asked for. */
    STATION_APPLICATION_NOT_HERE_ON_ACCEPTANCE(
            Area.STATIONS, 3, HttpStatus.NOT_FOUND, Sentences.STATION_APPLICATION_NOT_HERE),

    /** An application that was gone or already answered when its denial was asked for. */
    STATION_APPLICATION_NOT_HERE_ON_DENIAL(
            Area.STATIONS, 4, HttpStatus.NOT_FOUND, Sentences.STATION_APPLICATION_NOT_HERE),

    /**
     * A public address another station on this instance already answers at. Named because a screen
     * has to tell this one refusal apart from every other refusal a settings form can get, and
     * matching on the English sentence to do it broke the moment anybody reworded it.
     */
    STATION_SLUG_TAKEN(Area.STATIONS, 5, HttpStatus.CONFLICT, "Another station is already reached at that address"),

    /** A section of a protocol that is gone, or belongs to another station. */
    PROTOCOL_SECTION_NOT_HERE(Area.TEST_PROTOCOLS, 1, HttpStatus.NOT_FOUND, "That section is not here any more"),

    /** A point in a protocol that is gone, or belongs to another station. */
    PROTOCOL_ITEM_NOT_HERE(Area.TEST_PROTOCOLS, 2, HttpStatus.NOT_FOUND, "That point is not here any more"),

    /** A question paired with a waiting list it is not on. */
    WAITING_LIST_FIELD_NOT_IN_LIST(Area.WAITING_LISTS, 1, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_FIELD_NOT_HERE),

    /** An invite paired with a waiting list it does not belong to. */
    WAITING_LIST_INVITE_NOT_IN_LIST(Area.WAITING_LISTS, 2, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_INVITE_UNKNOWN),

    /**
     * An entry that is gone, or belongs to another waiting list. The two are one code deliberately:
     * telling them apart would say that the entry exists on a list the caller may not see.
     */
    WAITING_LIST_ENTRY_NOT_IN_LIST(Area.WAITING_LISTS, 3, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** An invite code no waiting list is reached by. */
    WAITING_LIST_INVITE_UNKNOWN(Area.WAITING_LISTS, 4, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_INVITE_UNKNOWN),

    /** The waiting list an invite names, which is gone. */
    WAITING_LIST_NOT_HERE_BEHIND_INVITE(Area.WAITING_LISTS, 5, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** An entry token that names no entry. */
    WAITING_LIST_ENTRY_TOKEN_UNKNOWN(
            Area.WAITING_LISTS, 6, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** The waiting list an entry belongs to, which is gone. */
    WAITING_LIST_NOT_HERE_BEHIND_ENTRY(Area.WAITING_LISTS, 7, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** The station whose invitation a family is reading, which is gone. */
    STATION_NOT_HERE_BEHIND_INVITATION(Area.WAITING_LISTS, 8, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** An invitation answered with a token that names no entry any more. */
    WAITING_LIST_ENTRY_NOT_HERE_ON_ANSWER(
            Area.WAITING_LISTS, 9, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** Signing up for the same waiting list far more often than a person could. */
    WAITING_LIST_TOO_OFTEN(Area.WAITING_LISTS, 10, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A waiting list that went between the ownership check and being read. */
    WAITING_LIST_NOT_HERE(Area.WAITING_LISTS, 11, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** A waiting list that went before the change to it could be written. */
    WAITING_LIST_NOT_CHANGED(Area.WAITING_LISTS, 12, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** A waiting list that went before its visible questions could be written. */
    WAITING_LIST_NOT_HERE_ON_VISIBLE_QUESTIONS(
            Area.WAITING_LISTS, 13, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** A question on a waiting list that went before the change to it could be written. */
    WAITING_LIST_FIELD_NOT_CHANGED(Area.WAITING_LISTS, 14, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_FIELD_NOT_HERE),

    /** A waiting list that went between its entries being read and its own details being read. */
    WAITING_LIST_NOT_HERE_ON_ENTRIES(Area.WAITING_LISTS, 15, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** An entry that went between being changed and being read back. */
    WAITING_LIST_ENTRY_NOT_HERE_AFTER_CHANGE(
            Area.WAITING_LISTS, 16, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** An entry that went between its date being changed and being read back. */
    WAITING_LIST_ENTRY_NOT_HERE_AFTER_DATE_CHANGE(
            Area.WAITING_LISTS, 17, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** A station that is not here, asked for by a public waiting list address. */
    STATION_NOT_HERE_BEHIND_PUBLIC_LIST(Area.WAITING_LISTS, 18, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Public waiting lists at all, at a station that has switched them off. */
    PUBLIC_WAITING_LISTS_SWITCHED_OFF(Area.WAITING_LISTS, 19, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /**
     * A waiting list that is gone, belongs to another station, or is not open to the public. One
     * code deliberately: telling them apart would say that a list nobody outside may see is there.
     */
    PUBLIC_WAITING_LIST_NOT_HERE(Area.WAITING_LISTS, 20, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** The same, at the moment a public sign-up is sent rather than when the form is drawn. */
    PUBLIC_WAITING_LIST_NOT_HERE_ON_REGISTRATION(
            Area.WAITING_LISTS, 21, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE);

    private final Area area;
    private final int number;
    private final String code;
    private final HttpStatus status;
    private final String message;

    Refusal(Area area, int number, HttpStatus status, String message) {
        this.area = area;
        this.number = number;
        this.code = "%s-%03d".formatted(area.letter(), number);
        this.status = status;
        this.message = message;
    }

    /**
     * The area of the product this refusal belongs to, which is what its letter stands for.
     *
     * @return the area
     */
    public Area area() {
        return area;
    }

    /**
     * This refusal's number within its area.
     *
     * @return the number, unique among the refusals of the same area
     */
    public int number() {
        return number;
    }

    /**
     * The code a reader quotes, which names exactly one line.
     *
     * @return the area's letter, a hyphen and three digits
     */
    public String code() {
        return code;
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
     * @return an exception carrying this refusal's status, sentence and code
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
     * @return an exception carrying this refusal's status and code, and its sentence with the
     *         detail named
     */
    public RefusalResponse raise(String detail) {
        return new RefusalResponse(this, message + ": " + detail);
    }

    /**
     * The registry of code letters, one per area of the product.
     *
     * <p>A letter is claimed here and nowhere else, so two features cannot both be {@code F} and
     * nobody has to read the whole enum to find out what is free. An area with no refusals yet is
     * still listed: reserving the letter is the point, because the alternative is two features
     * picking the same one months apart.
     */
    public enum Area {
        /** Instance administration and the peers an instance knows of. */
        ADMIN('A', "instance administration and peers"),
        /** The request body, before any route has looked at it. */
        BODY('B', "the request body"),
        /** The documents a station keeps for its members. */
        DOCUMENTS('D', "documents"),
        /** Appointments and the registrations for them. */
        EVENTS('E', "appointments and registrations"),
        /** Forms, their answers and the links they are answered through. */
        FORMS('F', "forms and answers"),
        /** Anything with no area of its own: faults, the store, and the shared loaders. */
        GENERAL('G', "failures that belong to no one feature"),
        /** Inventory, which has named no refusals yet. */
        INVENTORY('I', "inventory"),
        /** The knowledge base, which has named no refusals yet. */
        KNOWLEDGE_BASE('K', "the knowledge base"),
        /** The media library: files, folders, tags and uploads. */
        MEDIA_LIBRARY('L', "the media library"),
        /** Members, accounts, avatars and profile questions. */
        MEMBERS('M', "members and accounts"),
        /** Setting an instance up, before it has any station. */
        INSTALLATION('N', "installation"),
        /** Pages, their public addresses and their share links. */
        PAGES('P', "pages"),
        /** Catalogs, tests and the papers sat on them. */
        QUIZZES('Q', "quizzes"),
        /** Procedures and who they were handed to. */
        PROCEDURES('R', "procedures"),
        /** Stations and the applications to join one. */
        STATIONS('S', "stations"),
        /** Test protocols, their sections and their points. */
        TEST_PROTOCOLS('T', "test protocols"),
        /** Waiting lists, their entries and their invites. */
        WAITING_LISTS('W', "waiting lists"),
        /** Federation between instances, which has named no refusals yet. */
        FEDERATION('X', "federation");

        private final char letter;
        private final String covers;

        Area(char letter, String covers) {
            this.letter = letter;
            this.covers = covers;
        }

        /**
         * The letter this area's codes open with.
         *
         * @return one upper-case letter
         */
        public char letter() {
            return letter;
        }

        /**
         * What the letter stands for, in the words somebody looking up a code would use.
         *
         * @return the area in a few words
         */
        public String covers() {
            return covers;
        }
    }

    /**
     * The sentences more than one refusal says.
     *
     * <p>Several sites refuse for the same reason in different places, and each needs its own code
     * while saying the same thing. Written out at each constant the wording drifts: one of them is
     * reworded, the others are not, and the same failure reads two ways. Named here it cannot.
     */
    private static final class Sentences {
        private static final String STATION_NOT_HERE = "That station is not here";
        private static final String NOT_HERE_OR_NOT_YOURS = "That is not here any more, or it is not yours to open";
        private static final String UNEXPECTED_FAULT =
                "Something went wrong in Ember and the request was not carried out, so nothing was saved. "
                        + "Trying again may work; if it keeps happening, please report it";
        private static final String NO_STATION_CHOSEN = "Choose a station before doing this";
        private static final String TOO_MANY_ATTEMPTS = "Too many attempts from here. Try again shortly";
        private static final String PEER_DID_NOT_ANSWER = "That instance could not be reached";

        private static final String FORM_NOT_HERE = "That form is not here any more, so nothing was changed";
        private static final String FORM_NOT_ANSWERED_FROM_OUTSIDE =
                "That form cannot be answered from outside the station";

        private static final String EVENT_NOT_HERE = "That appointment is not here any more";
        private static final String EVENT_REGISTRATION_NOT_HERE = "That registration is not here any more";

        private static final String PAGE_NOT_HERE = "That page is not here any more";

        private static final String FILE_NOT_HERE = "That file is not here any more";
        private static final String FOLDER_NOT_HERE = "That folder is not here any more";
        private static final String FILE_TAG_NOT_HERE = "That tag is not here any more";
        private static final String PICTURE_NOT_HERE = "That picture is not here";
        private static final String UPLOAD_MISSING_FILE = "The upload arrived without a file in it";
        private static final String UPLOAD_NOT_SAVED = "The upload could not be saved";
        private static final String UPLOAD_NOT_PROCESSED =
                "The upload could not be worked through, so nothing was saved. Trying again may work";

        private static final String SESSION_HAS_NO_ACCOUNT = "This session does not stand for an account";
        private static final String MEMBER_NOT_HERE = "That member is not here any more";
        private static final String ACCOUNT_NOT_HERE = "That account is not here any more";
        private static final String PROFILE_FIELD_NOT_HERE = "That profile question is not here any more";

        private static final String DOCUMENT_NOT_HERE = "That document is not here any more";
        private static final String DOCUMENT_NOT_YOURS = "That document belongs to somebody you do not answer for";
        private static final String DOCUMENT_NOT_YOURS_TO_ADD = "You may not add a document for this member";

        private static final String QUIZ_CATALOG_NOT_HERE = "That catalog is not here any more";
        private static final String QUIZ_CATEGORY_NOT_HERE = "That category is not here any more";
        private static final String QUIZ_TEST_NOT_HERE = "That test is not here any more";
        private static final String QUIZ_ATTEMPT_NOT_HERE = "That attempt is not here any more";

        private static final String PROCEDURE_NOT_YOURS = "That procedure was not handed to you";

        private static final String STATION_APPLICATION_NOT_HERE = "That application is not here any more";

        private static final String WAITING_LIST_NOT_HERE = "That waiting list is not here any more";
        private static final String WAITING_LIST_ENTRY_NOT_HERE = "That entry is not here any more";
        private static final String WAITING_LIST_FIELD_NOT_HERE = "That question is not on this waiting list any more";
        private static final String WAITING_LIST_INVITE_UNKNOWN = "No waiting list is reached by this invite";

        private Sentences() {}
    }
}
