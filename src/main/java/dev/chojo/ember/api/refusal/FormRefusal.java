/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#FORMS}: forms and answers.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum FormRefusal implements Refusal {
    /**
     * A form asked for by a route that then checks whose it is. The ownership check behind it
     * answers {@link GeneralRefusal#NOT_YOURS_TO_OPEN}, which is the shared answer for a thing that is somebody
     * else's.
     */
    FORM_NOT_HERE(1, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form that went between an act on it succeeding and the answer being drawn. */
    FORM_NOT_HERE_ON_REREAD(2, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form that went between the screen being opened and the change being saved. */
    FORM_NOT_HERE_ON_CHANGE(3, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form that was already gone when its deletion was asked for. */
    FORM_NOT_HERE_ON_DELETE(4, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form that went before how far it reaches could be written. */
    FORM_NOT_HERE_ON_VISIBILITY_CHANGE(5, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form that went before it could be closed. */
    FORM_NOT_HERE_ON_CLOSE(6, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** The station an export of answers is headed with, gone between the two reads. */
    STATION_NOT_HERE_FOR_FORM_EXPORT(7, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** The station a shared form belongs to, asked for to draw its name and colours. */
    STATION_NOT_HERE_BEHIND_FORM_LINK(8, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A link that no form is reached by, which covers a link that has since been replaced. */
    FORM_LINK_UNKNOWN(9, HttpStatus.NOT_FOUND, "No form is reached by this link"),

    /** A form that is closed, not yet open, or never published. */
    FORM_NOT_TAKING_ANSWERS(10, HttpStatus.GONE, "This form is not taking answers"),

    /** Answering the same public form far more often than a person could. */
    FORM_ANSWERED_TOO_OFTEN(
            11, HttpStatus.TOO_MANY_REQUESTS, "This form has been answered too often from here. Try again shortly"),

    /** A form somebody has already answered once, where once is all it takes. */
    FORM_ALREADY_ANSWERED(12, HttpStatus.CONFLICT, "You have already answered this form"),

    /** Answers that were read and then refused for what they said. */
    FORM_ANSWER_REFUSED(13, HttpStatus.BAD_REQUEST, "The answers could not be saved"),

    /** Answers that arrived in a shape nothing could be made of. */
    FORM_ANSWER_UNREADABLE(
            14,
            HttpStatus.BAD_REQUEST,
            "The answers could not be read, so nothing was saved. Fill the form in again and send it once more"),

    /** The station a public form address names, which is not here. */
    STATION_NOT_HERE_BEHIND_PUBLIC_FORM(15, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /**
     * A public form that is gone, or belongs to a station other than the one in the address. The
     * two are one code deliberately: telling them apart would say that the form exists elsewhere.
     */
    PUBLIC_FORM_NOT_HERE(16, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form of a kind strangers may not answer. */
    FORM_NOT_ANSWERED_FROM_OUTSIDE(17, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_ANSWERED_FROM_OUTSIDE),

    /** A form that reaches less far than an open address needs it to. */
    FORM_NOT_OPENLY_ADDRESSED(18, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_ANSWERED_FROM_OUTSIDE),

    /** A cell on a page pointing at a form that is gone or is of the wrong kind. */
    PAGE_FORM_NOT_HERE(19, HttpStatus.NOT_FOUND, "The form this part of the page shows is not here any more"),

    /**
     * An answer that was to be acknowledged and is gone, or belongs to another form. The two are
     * one code deliberately: telling them apart would say that the answer exists elsewhere.
     */
    FORM_ANSWER_NOT_HERE(20, HttpStatus.NOT_FOUND, "That answer is not here any more"),

    /** A kind of form asked for by a name that names none. */
    FORM_KIND_UNKNOWN(21, HttpStatus.BAD_REQUEST, Sentences.FORM_KIND_UNKNOWN),

    /** A search for forms that did not say which kind it wants. */
    FORM_KIND_NOT_NAMED(22, HttpStatus.BAD_REQUEST, "Say which kind of form to look for"),

    /** The same kind that names none, at the picker rather than at the list. */
    FORM_KIND_UNKNOWN_ON_SEARCH(23, HttpStatus.BAD_REQUEST, Sentences.FORM_KIND_UNKNOWN),

    /** A form offered without a title. */
    FORM_NEEDS_A_TITLE(24, HttpStatus.BAD_REQUEST, "A form needs a title, so nothing was saved"),

    /** Publishing a form that is past being a draft. */
    FORM_NOT_A_DRAFT(26, HttpStatus.BAD_REQUEST, "Only a form that is still a draft can be published"),

    /** How far a form reaches, left unsaid where it had to be said. */
    FORM_REACH_NOT_SAID(27, HttpStatus.BAD_REQUEST, "Say how far the form is to reach"),

    /** The link of a form the station's own members answer, which is not sent by one. */
    INTERNAL_FORM_HAS_NO_LINK(28, HttpStatus.BAD_REQUEST, Sentences.INTERNAL_FORM_HAS_NO_LINK),

    /** Replacing a link that has been replaced by somebody else in the meantime. */
    FORM_LINK_ALREADY_REPLACED(
            29, HttpStatus.CONFLICT, "This form has been given a different link since you last looked"),

    /** Questions of a kind the form's own kind does not ask. */
    QUESTIONS_NOT_FOR_THIS_KIND_OF_FORM(
            30,
            HttpStatus.BAD_REQUEST,
            "Some of these questions cannot be asked on a form of this kind, so nothing was saved"),

    /** A form that is closed, not yet open or never published, answered by a member. */
    FORM_TAKES_NO_ANSWERS(33, HttpStatus.BAD_REQUEST, Sentences.FORM_TAKES_NO_ANSWERS),

    /** A form the member answering is not among the people it was put to. */
    FORM_NOT_YOURS_TO_ANSWER(34, HttpStatus.FORBIDDEN, Sentences.FORM_NOT_YOURS_TO_ANSWER),

    /** Answers that were read and then refused for what they said. */
    FORM_ANSWERS_NOT_SAVED(35, HttpStatus.BAD_REQUEST, Sentences.FORM_ANSWERS_NOT_SAVED),

    /** A form that takes an answer once and does not take it again. */
    FORM_ANSWER_NOT_CHANGEABLE(37, HttpStatus.BAD_REQUEST, Sentences.FORM_ANSWER_NOT_CHANGEABLE),

    /** The same form put to somebody else, at the moment an answer to it is changed. */
    FORM_NOT_YOURS_TO_CHANGE_ANSWER(38, HttpStatus.FORBIDDEN, Sentences.FORM_NOT_YOURS_TO_ANSWER),

    /** A changed answer that was read and then refused for what it said. */
    FORM_ANSWER_CHANGE_NOT_SAVED(39, HttpStatus.BAD_REQUEST, Sentences.FORM_ANSWERS_NOT_SAVED),

    /** A form that takes no answers, at the moment one is given for somebody looked after. */
    FORM_TAKES_NO_ANSWERS_FOR_MEMBER(41, HttpStatus.BAD_REQUEST, Sentences.FORM_TAKES_NO_ANSWERS),

    /** A form whose answer stands, at the moment it is changed for somebody looked after. */
    FORM_ANSWER_NOT_CHANGEABLE_FOR_MEMBER(42, HttpStatus.BAD_REQUEST, Sentences.FORM_ANSWER_NOT_CHANGEABLE),

    /** A form that was not put to the member it is being answered for. */
    FORM_NOT_FOR_THIS_MEMBER(43, HttpStatus.FORBIDDEN, "This form was not put to the member you are answering for"),

    /** Answers given for somebody looked after, read and then refused for what they said. */
    FORM_ANSWERS_FOR_MEMBER_NOT_SAVED(44, HttpStatus.BAD_REQUEST, Sentences.FORM_ANSWERS_NOT_SAVED),

    /** Answering for a member nobody has put in the caller's care. */
    MEMBER_NOT_YOURS_TO_ANSWER_FOR(45, HttpStatus.FORBIDDEN, Sentences.MEMBER_NOT_YOURS),

    /** Grouping results by who answered, on a form that is answered without signing in. */
    ONLY_INTERNAL_FORM_GROUPED_BY_WHO_ANSWERED(
            46, HttpStatus.BAD_REQUEST, "Only the results of an internal form can be grouped by who answered"),

    /** An export of answers that fell over while it was being written, which is ours to look into. */
    FORM_ANSWERS_NOT_EXPORTED(47, HttpStatus.INTERNAL_SERVER_ERROR, "The answers could not be made into a file"),

    /** A form that could not be read back after how far it reaches was changed, with the change already in. */
    FORM_NOT_HERE_AFTER_VISIBILITY_CHANGE(
            48, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** Saving a form's questions with one that names a question of some other form, or none that exists. */
    QUESTION_NOT_ON_THIS_FORM(
            49, HttpStatus.BAD_REQUEST, "Some of these questions are not on this form, so nothing was saved"),

    /** Saving a question that already exists under a different type than the one it was asked as. */
    QUESTION_TYPE_NOT_CHANGEABLE(
            50, HttpStatus.BAD_REQUEST, "A question that already exists keeps its type, so nothing was saved"),

    /**
     * A first answer given by a member, or for somebody they look after, who has answered already.
     * The answer on file is changed, where the form allows it, and never replaced by a second first
     * answer.
     */
    FORM_ANSWER_ALREADY_ON_FILE(51, HttpStatus.CONFLICT, "This form has already been answered, so nothing was saved"),

    /**
     * Saving a question whose options or statements do not each carry a key of their own. Answers
     * name an option by its key, so an option without one, or two sharing one, cannot be told apart.
     */
    QUESTION_OPTION_KEYS_NOT_DISTINCT(
            53, HttpStatus.BAD_REQUEST, "Every option of a question needs a key of its own, so nothing was saved"),

    /**
     * Saving a form without a page, or with pages that do not each carry a key of their own. The
     * page that follows and the path a response took name a page by its key.
     */
    FORM_PAGE_KEYS_NOT_DISTINCT(
            54,
            HttpStatus.BAD_REQUEST,
            "A form needs at least one page, and every page a key of its own, so nothing was saved"),

    /**
     * A page that is to be followed by a page that is not further down, or by one the form does not
     * have. Pages only lead forward, which is what keeps every path through a form finite.
     */
    FORM_PAGE_TARGET_NOT_FURTHER_DOWN(
            55, HttpStatus.BAD_REQUEST, "A page can only lead to a page further down, so nothing was saved"),

    /** A question put on a page the form does not have. */
    QUESTION_ON_NO_PAGE(
            56,
            HttpStatus.BAD_REQUEST,
            "Some of these questions stand on a page the form does not have, so nothing was saved"),

    /**
     * A question that is to decide where its page leads but is not a single-answer choice, or names
     * options it does not have. Only one picked option can say which page comes next.
     */
    QUESTION_BRANCH_NOT_ON_A_SINGLE_CHOICE(
            57,
            HttpStatus.BAD_REQUEST,
            "Only a question with one answer from its own options can decide the next page, so nothing was saved"),

    /** A page on which more than one question is to decide where it leads. */
    PAGE_BRANCHES_ON_TWO_QUESTIONS(
            58, HttpStatus.BAD_REQUEST, "Only one question per page can decide the next page, so nothing was saved"),

    /** A required question on a page the answers went through, left without an answer. */
    QUESTION_NEEDS_AN_ANSWER(59, HttpStatus.BAD_REQUEST, "This question needs an answer"),

    /** An answer that does not fit its question: an option it does not have, too many picks, a rating off the scale. */
    ANSWER_DOES_NOT_FIT_QUESTION(60, HttpStatus.BAD_REQUEST, "This answer does not fit the question"),

    /** An answer to a question the form does not have. */
    ANSWER_TO_QUESTION_NOT_ON_FORM(
            61, HttpStatus.BAD_REQUEST, "This answer belongs to a question the form does not have"),

    /** A copy of a form asked for without a title. */
    FORM_COPY_NEEDS_A_TITLE(62, HttpStatus.BAD_REQUEST, "A copy needs a title, so nothing was copied"),

    /** A form that went before it could be copied. */
    FORM_NOT_HERE_ON_COPY(64, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /**
     * A link offered after sending a form that is neither a web address nor an address on this site.
     * The link is put in front of strangers, so nothing else is let through.
     */
    FORM_COMPLETION_LINK_NOT_A_LINK(
            65,
            HttpStatus.BAD_REQUEST,
            "The link after sending has to be a web address or an address on this site, so nothing was saved"),

    /** A half-filled form kept for later on a form that is not taking answers. */
    FORM_TAKES_NO_DRAFTS(67, HttpStatus.BAD_REQUEST, Sentences.FORM_TAKES_NO_ANSWERS),

    /** A half-filled form kept by a member the form was not put to. */
    FORM_NOT_YOURS_TO_DRAFT(68, HttpStatus.FORBIDDEN, Sentences.FORM_NOT_YOURS_TO_ANSWER),

    /**
     * Reading, keeping or ending another member's half-filled form without looking after them. An
     * unsent answer is private: managing the station's polls does not reach it.
     */
    FORM_DRAFT_NOT_YOURS(
            69,
            HttpStatus.FORBIDDEN,
            "Only the member and whoever looks after them can see or keep an answer that was not sent"),

    /** A new link asked for a form the station's own members answer, which is not sent by one. */
    FORM_INTERNAL_HAS_NO_LINK(70, HttpStatus.BAD_REQUEST, Sentences.INTERNAL_FORM_HAS_NO_LINK),

    /** How far it reaches, set on a form the station's own members answer. */
    FORM_INTERNAL_HAS_NO_REACH(
            71,
            HttpStatus.BAD_REQUEST,
            "A form for the station's own members is not reached from outside at all, so nothing was saved"),

    /** Who may answer, narrowed on a form that is not here any more. */
    FORM_NOT_HERE_FOR_RESTRICTIONS(72, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** Who may answer, narrowed on a form answered from outside the station. */
    FORM_FROM_OUTSIDE_HAS_NO_RESTRICTIONS(
            73,
            HttpStatus.BAD_REQUEST,
            "A form answered from outside the station has nobody to narrow it to, so nothing was saved"),

    /** Results asked to be grouped without saying by what. */
    FORM_RESULTS_GROUPING_MISSING(74, HttpStatus.BAD_REQUEST, "Say what to group the results by"),

    /** Results grouped by a profile question that is not here or cannot be grouped by. */
    FORM_RESULTS_GROUPING_FIELD_NOT_HERE(
            75, HttpStatus.BAD_REQUEST, "The results cannot be grouped by that profile question"),

    /** A form whose results were asked for, gone before they could be counted. */
    FORM_NOT_HERE_FOR_ANALYTICS(76, HttpStatus.NOT_FOUND, "That form is not here any more"),

    /** One response to a form opened, which the form does not have. */
    FORM_RESPONSE_NOT_HERE(77, HttpStatus.NOT_FOUND, "That answer to the form is not here any more");

    private final Definition definition;

    FormRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.FORMS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
