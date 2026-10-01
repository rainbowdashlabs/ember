/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#PASSKEYS}: passkeys.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum PasskeyRefusal implements Refusal {
    /** A sign-in vouched for by another device that arrived without the claim to spend. */
    DEVICE_SIGN_IN_CLAIM_MISSING(1, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /**
     * A sign-in vouched for by another device that did not end in a session. One code and one
     * sentence for a claim nobody ever handed out, a claim already spent, and an account the
     * sign-in was then refused for: telling them apart would say whether a guessed claim was real
     * and what state the account behind it is in.
     */
    DEVICE_SIGN_IN_NOT_GRANTED(
            2, HttpStatus.UNAUTHORIZED, "That sign-in could not be completed. Ask the other device again"),

    /** A device asking whether it has been let in yet without saying which request it is. */
    DEVICE_POLL_SECRET_MISSING(3, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** A passkey being made on a new device without the permission the handshake hands over. */
    DEVICE_ENROLMENT_TOKEN_MISSING(4, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /**
     * A permission to make a passkey on a new device that is unknown, has run out or has already
     * been spent. One code deliberately: telling them apart would say which guesses were once real.
     */
    DEVICE_ENROLMENT_NOT_BEGUN(5, HttpStatus.UNAUTHORIZED, Sentences.PASSKEY_ENROLMENT_REFUSED),

    /** A passkey being finished on a new device with one of the three parts left out. */
    DEVICE_ENROLMENT_DETAILS_MISSING(6, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /**
     * A passkey on a new device that the browser's answer did not complete. One code for a spent
     * permission, a challenge that does not belong to it and an answer that did not verify.
     */
    DEVICE_ENROLMENT_NOT_FINISHED(7, HttpStatus.UNAUTHORIZED, Sentences.PASSKEY_ENROLMENT_REFUSED),

    /** Reading whose account an enrolment link is for, without the link. */
    ENROLMENT_LINK_TOKEN_MISSING(8, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /**
     * An enrolment link that names nobody. One code for unknown, run out and already spent, because
     * this is the one place that would otherwise say a name, and guessing must earn nothing.
     */
    ENROLMENT_LINK_UNKNOWN(9, HttpStatus.NOT_FOUND, Sentences.ENROLMENT_LINK_NOT_GOOD),

    /** A passkey being started from an enrolment link that the request did not carry. */
    ENROLMENT_LINK_TOKEN_MISSING_ON_BEGIN(10, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** An enrolment link that could not begin a passkey, for every reason it cannot. */
    ENROLMENT_LINK_NOT_BEGUN(11, HttpStatus.UNAUTHORIZED, Sentences.PASSKEY_ENROLMENT_REFUSED),

    /** A passkey being finished from an enrolment link with one of the three parts left out. */
    ENROLMENT_LINK_DETAILS_MISSING(12, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** An enrolment link whose passkey the browser's answer did not complete. */
    ENROLMENT_LINK_NOT_FINISHED(13, HttpStatus.UNAUTHORIZED, Sentences.PASSKEY_ENROLMENT_REFUSED),

    /** Reading what a device is asking for without typing the code it is showing. */
    DEVICE_CODE_MISSING_ON_LOOKUP(14, HttpStatus.BAD_REQUEST, Sentences.DEVICE_CODE_MISSING),

    /**
     * A device code that is not open, or is open for somebody this reader does not answer for. One
     * code and one sentence deliberately: a wrong code and somebody else's code have always
     * answered alike, and splitting them would turn this into a way of asking whether a code exists.
     */
    DEVICE_CODE_NOT_YOURS_ON_LOOKUP(15, HttpStatus.NOT_FOUND, Sentences.DEVICE_CODE_NOT_GOOD),

    /** Confirming for somebody else something that is not a sign-in. */
    APPROVAL_ONLY_FOR_SIGN_IN(16, HttpStatus.FORBIDDEN, "Only a sign-in can be confirmed for somebody else"),

    /** Signing somebody in who is not in this reader's care. */
    APPROVAL_MEMBER_NOT_YOURS(17, HttpStatus.FORBIDDEN, Sentences.MEMBER_NOT_YOURS),

    /** Confirming what a device is asking for without typing the code it is showing. */
    DEVICE_CODE_MISSING_ON_APPROVAL(18, HttpStatus.BAD_REQUEST, Sentences.DEVICE_CODE_MISSING),

    /**
     * A device code being confirmed that is not open, or is open for somebody this reader does not
     * answer for. One code and one sentence, for the same reason the lookup has one.
     */
    DEVICE_CODE_NOT_YOURS_ON_APPROVAL(19, HttpStatus.NOT_FOUND, Sentences.DEVICE_CODE_NOT_GOOD),

    /** A confirmation that named none of the numbers offered. */
    DEVICE_MATCH_NUMBER_MISSING(20, HttpStatus.BAD_REQUEST, "Choose the number the other device is showing"),

    /** A confirmation that named the wrong one of the numbers offered. */
    DEVICE_MATCH_NUMBER_WRONG(21, HttpStatus.CONFLICT, "That is not the number the other device is showing"),

    /** A device request that ran out or was answered elsewhere between being read and confirmed. */
    DEVICE_APPROVAL_NOT_TAKEN(
            22, HttpStatus.NOT_FOUND, "That request could not be confirmed any more, so nothing was granted"),

    /** Anything to do with passkeys on an instance that has switched them off. */
    PASSKEYS_SWITCHED_OFF(23, HttpStatus.FORBIDDEN, "Passkeys are switched off on this instance"),

    /** A passwordless sign-in finished without the challenge or the browser's answer. */
    PASSKEY_SIGN_IN_DETAILS_MISSING(24, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /**
     * A passwordless sign-in that did not work, for every reason it does not work: an unknown
     * passkey, a signature that did not verify, a passkey that may not sign in, an address nobody
     * has confirmed. One code and one sentence deliberately, because anybody grinding this endpoint
     * must be told the same thing every time or it becomes a list of who has a passkey here.
     */
    PASSKEY_SIGN_IN_REFUSED(
            25, HttpStatus.UNAUTHORIZED, "Signing in with that passkey did not work. Try another way in"),

    /** A passkey being made for a session whose account went in between. */
    ACCOUNT_NOT_HERE_ON_PASSKEY_CREATION(26, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** A passkey being finished without the challenge or the browser's answer. */
    PASSKEY_CREATION_DETAILS_MISSING(27, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** A passkey the browser's answer did not complete, so none was saved. */
    PASSKEY_NOT_CREATED(28, HttpStatus.BAD_REQUEST, "That passkey could not be created, so none was saved"),

    /** A passkey that went between the list being drawn and the new name being given. */
    PASSKEY_NOT_HERE_ON_RENAME(29, HttpStatus.NOT_FOUND, Sentences.PASSKEY_NOT_HERE),

    /** A passkey that was already gone when its removal was asked for. */
    PASSKEY_NOT_HERE_ON_REMOVAL(30, HttpStatus.NOT_FOUND, Sentences.PASSKEY_NOT_HERE),

    /** The last passkey on an account that has no password to fall back on. */
    LAST_WAY_INTO_ACCOUNT(
            31,
            HttpStatus.CONFLICT,
            "This is the only way into the account, so it was kept. Be onboarded again for a new passkey first"),

    /** Switching password sign-in off where the instance does not allow that at all. */
    PASSWORD_SIGN_IN_LOCKED_BY_INSTANCE(
            32,
            HttpStatus.FORBIDDEN,
            "This instance does not allow switching password sign-in off, so nothing was changed"),

    /** Switching password sign-in off on an account no reset mail could reach. */
    PASSWORD_SIGN_IN_NEEDS_REACHABLE_ADDRESS(
            33,
            HttpStatus.CONFLICT,
            "Switching password sign-in off needs an address a reset mail can reach, so nothing was changed"),

    /** Switching password sign-in off before any passkey has been shown to work. */
    PASSWORD_SIGN_IN_NEEDS_TRIED_PASSKEY(
            34,
            HttpStatus.CONFLICT,
            "Switching password sign-in off needs a passkey that has signed in once, so nothing was changed"),

    /** Switching password sign-in on an account that holds no password to switch. */
    ACCOUNT_HOLDS_NO_PASSWORD_ON_SWITCH(35, HttpStatus.CONFLICT, Sentences.ACCOUNT_HOLDS_NO_PASSWORD),

    /** A reply to the passkey offer that gives no answer. */
    PASSKEY_OFFER_ANSWER_UNKNOWN(36, HttpStatus.BAD_REQUEST, "That is not an answer this offer takes"),

    /** A passkey being tried out without the challenge or the browser's answer. */
    PASSKEY_TRIAL_DETAILS_MISSING(37, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** Retiring the password of an account that holds none. */
    ACCOUNT_HOLDS_NO_PASSWORD_ON_RETIRE(38, HttpStatus.CONFLICT, Sentences.ACCOUNT_HOLDS_NO_PASSWORD),

    /** Retiring a password before any passkey on that account has been shown to work. */
    NO_TRIED_PASSKEY_ON_RETIRE(
            39, HttpStatus.CONFLICT, "No passkey of that account has signed in yet, so the password was kept"),

    /** A change of the passkey mode that names no mode. */
    PASSKEY_MODE_UNKNOWN(40, HttpStatus.BAD_REQUEST, "That is not a passkey mode this instance knows"),

    /** Going passwordless on an instance whose mail has never been shown to work. */
    PASSWORDLESS_NEEDS_WORKING_MAIL(
            41, HttpStatus.CONFLICT, "Going passwordless needs working mail, proven by a test mail that went out"),

    /** Lowering the passkey mode while accounts would be left with no way in. */
    PASSWORDLESS_ACCOUNTS_DEPEND(
            42,
            HttpStatus.CONFLICT,
            "Some accounts would have no way in without a passkey, so the mode was left as it was"),

    /** A passkey set up on another device, asked for far more often than a person could. */
    PASSKEY_DEVICE_REQUEST_TOO_OFTEN(43, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A sign-in through another device asked for far more often than a person could. */
    PASSKEY_SIGN_IN_REQUEST_TOO_OFTEN(44, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A sign-in another device approved, collected far more often than a person could. */
    PASSKEY_SIGN_IN_CLAIM_TOO_OFTEN(45, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Waiting on another device's approval, asked after far more often than a screen would. */
    PASSKEY_DEVICE_POLL_TOO_OFTEN(46, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Setting up a passkey on an approved device, started far more often than a person could. */
    PASSKEY_DEVICE_ENROLL_BEGIN_TOO_OFTEN(47, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Setting up a passkey on an approved device, finished far more often than a person could. */
    PASSKEY_DEVICE_ENROLL_FINISH_TOO_OFTEN(48, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A passkey setup link looked up far more often than a person could. */
    PASSKEY_TOKEN_LOOKUP_TOO_OFTEN(49, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Setting up a passkey through a link, started far more often than a person could. */
    PASSKEY_TOKEN_ENROLL_BEGIN_TOO_OFTEN(50, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Setting up a passkey through a link, finished far more often than a person could. */
    PASSKEY_TOKEN_ENROLL_FINISH_TOO_OFTEN(51, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A code shown on another device, looked up far more often than a person could type one. */
    PASSKEY_DEVICE_CODE_LOOKUP_TOO_OFTEN(52, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Another device approved far more often than a person could. */
    PASSKEY_DEVICE_APPROVAL_TOO_OFTEN(53, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Signing in with a passkey, started far more often than a person could. */
    PASSKEY_SIGN_IN_BEGIN_TOO_OFTEN(54, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Signing in with a passkey, finished far more often than a person could. */
    PASSKEY_SIGN_IN_FINISH_TOO_OFTEN(55, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS);

    private final Definition definition;

    PasskeyRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.PASSKEYS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
