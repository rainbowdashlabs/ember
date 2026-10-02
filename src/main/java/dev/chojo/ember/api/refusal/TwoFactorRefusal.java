/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#TWO_FACTOR}: two-factor.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum TwoFactorRefusal implements Refusal {
    /** Setting up an app code on an account that already has a second factor. */
    ALREADY_ENROLLED_ON_TOTP_SETUP(1, HttpStatus.BAD_REQUEST, "A second factor is already set up on this account"),

    /** An app code being confirmed without the secret, the code or the recovery codes. */
    TOTP_CONFIRMATION_DETAILS_MISSING(2, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** An app code that did not match while it was being set up. */
    TOTP_SETUP_CODE_WRONG(3, HttpStatus.BAD_REQUEST, Sentences.VERIFICATION_CODE_WRONG),

    /** Removing an app code from an account that has none set up. */
    NO_TOTP_TO_REMOVE(4, HttpStatus.BAD_REQUEST, "There is no app code set up on this account to remove"),

    /** New recovery codes asked for on an account with no second factor. */
    NOT_ENROLLED_ON_BACKUP_CODES(5, HttpStatus.BAD_REQUEST, Sentences.NO_SECOND_FACTOR_SET_UP),

    /** A second factor being answered without saying which sign-in it is for, or with what. */
    TWO_FACTOR_CHECK_DETAILS_MISSING(6, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /**
     * A sign-in waiting on a second factor that is unknown, has run out, or was never waiting on
     * one. One code and one sentence deliberately: telling them apart would say whether a guessed
     * half-finished sign-in was ever real, which is exactly what this step withholds.
     */
    SIGN_IN_NOT_WAITING_ON_A_FACTOR(7, HttpStatus.UNAUTHORIZED, Sentences.SIGN_IN_NOT_WAITING),

    /**
     * A second factor that did not match at sign-in. One code for a wrong app code, a wrong
     * recovery code and a recovery code already spent, so no answer says which of the two was tried
     * or whether it had been used before.
     */
    TWO_FACTOR_CODE_WRONG(8, HttpStatus.UNAUTHORIZED, Sentences.VERIFICATION_CODE_WRONG),

    /** Withdrawing trust from a device that is not one this account trusts. */
    TRUSTED_DEVICE_NOT_HERE(9, HttpStatus.BAD_REQUEST, "That device is not one this account trusts any more"),

    /** Proving who you are again without saying with what, or with which proof. */
    STEP_UP_DETAILS_MISSING(10, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** Proving a second factor again on an account that has none set up. */
    NOT_ENROLLED_ON_STEP_UP(11, HttpStatus.BAD_REQUEST, Sentences.NO_SECOND_FACTOR_SET_UP),

    /** A second factor that did not match while proving who you are again. */
    STEP_UP_CODE_WRONG(12, HttpStatus.UNAUTHORIZED, Sentences.VERIFICATION_CODE_WRONG),

    /** A security key being set up without the challenge or the browser's answer. */
    SECURITY_KEY_SETUP_DETAILS_MISSING(13, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** A security key the browser's answer did not set up, so nothing was saved. */
    SECURITY_KEY_NOT_REGISTERED(
            14, HttpStatus.BAD_REQUEST, "That security key could not be set up, so nothing was saved"),

    /** A second factor that was already gone when its removal was asked for. */
    FACTOR_NOT_HERE_ON_REMOVAL(15, HttpStatus.BAD_REQUEST, Sentences.FACTOR_NOT_HERE),

    /**
     * A second factor that could not take the new name. One code for a name the product will not
     * take and a factor that is gone or belongs to somebody else, so no answer says which.
     */
    FACTOR_NOT_RENAMED(
            16, HttpStatus.BAD_REQUEST, "That name could not be given, so the second factor was left as it was"),

    /** A security key sign-in begun without saying which sign-in is waiting. */
    SECURITY_KEY_SIGN_IN_TOKEN_MISSING(17, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** A security key sign-in finished with one of the three parts left out. */
    SECURITY_KEY_SIGN_IN_DETAILS_MISSING(18, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** A security key that did not verify at sign-in. */
    SECURITY_KEY_SIGN_IN_REFUSED(19, HttpStatus.UNAUTHORIZED, Sentences.SECURITY_KEY_NOT_ACCEPTED),

    /** A security key proving who you are again, without the challenge or the browser's answer. */
    SECURITY_KEY_STEP_UP_DETAILS_MISSING(20, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** A security key that did not verify while proving who you are again. */
    SECURITY_KEY_STEP_UP_REFUSED(21, HttpStatus.UNAUTHORIZED, Sentences.SECURITY_KEY_NOT_ACCEPTED),

    /**
     * A security key ceremony whose waiting sign-in is unknown, has run out, or was never waiting on
     * a factor. One code for all three, for the same reason the code path has one.
     */
    SIGN_IN_NOT_WAITING_ON_A_KEY(22, HttpStatus.UNAUTHORIZED, Sentences.SIGN_IN_NOT_WAITING),

    /** Asking another device to confirm where this account has no other device that could. */
    NO_OTHER_DEVICE_TO_CONFIRM(23, HttpStatus.FORBIDDEN, "No other device of this account could confirm this"),

    /** Asking whether the other device has confirmed yet without saying which request. */
    DEVICE_STEP_UP_POLL_SECRET_MISSING(24, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** Asking another device to confirm without saying what it would be confirming. */
    STEP_UP_CATEGORY_MISSING(25, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** Naming something to confirm that this instance does not ask anybody to confirm. */
    STEP_UP_CATEGORY_UNKNOWN(26, HttpStatus.BAD_REQUEST, "That is not something this instance asks you to confirm"),

    /** Confirming with a password that the request did not carry. */
    STEP_UP_PASSWORD_MISSING(27, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /**
     * A password offered where this account is asked for something stronger. It says no more than
     * the screen the reader is already looking at, which offers only the proofs the account has:
     * accepting a password here would let somebody holding a phished one walk past a second factor.
     */
    PASSWORD_IS_NOT_A_PROOF_HERE(28, HttpStatus.FORBIDDEN, "A password is not enough to confirm this on this account"),

    /** A password that did not match while confirming who you are. */
    STEP_UP_PASSWORD_WRONG(29, HttpStatus.UNAUTHORIZED, "That password was not right, so nothing was confirmed"),

    /**
     * A passkey offered to confirm on an account that holds none. Says no more than the screen the
     * reader is already looking at, which offers the passkey button only where there is a passkey.
     */
    NO_PASSKEY_TO_CONFIRM_WITH(30, HttpStatus.FORBIDDEN, "This account holds no passkey to confirm with"),

    /** A passkey confirming who you are, without the challenge or the browser's answer. */
    PASSKEY_STEP_UP_DETAILS_MISSING(31, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** A passkey that did not verify while confirming who you are. */
    PASSKEY_STEP_UP_REFUSED(32, HttpStatus.UNAUTHORIZED, "That passkey did not confirm it, so nothing was confirmed"),

    /** An instance-wide rule that was already gone when its removal was asked for. */
    POLICY_NOT_HERE(35, HttpStatus.BAD_REQUEST, Sentences.POLICY_NOT_HERE),

    /**
     * A station rule that is gone, or belongs to another station. One code and one sentence
     * deliberately: splitting them would tell a station administrator that a rule with that number
     * exists on a station they cannot see.
     */
    POLICY_NOT_HERE_ON_STATION_DELETE(36, HttpStatus.BAD_REQUEST, Sentences.POLICY_NOT_HERE),

    /** A second factor an operator asked to clear that could not be cleared. */
    SECOND_FACTOR_NOT_RESET(37, HttpStatus.NOT_FOUND, Sentences.SECOND_FACTOR_NOT_RESET),

    /**
     * Somebody whose second factor a station administrator cannot clear. One code and one sentence
     * for a member of another station and an account that is not here at all, so this endpoint
     * cannot be used to find out which accounts exist.
     */
    MEMBER_NOT_YOURS_TO_RESET(39, HttpStatus.NOT_FOUND, "That member is not on your station, so nothing was changed"),

    /** A station administrator clearing the second factor of an instance administrator. */
    ADMIN_ONLY_RESET_BY_ADMIN(
            40,
            HttpStatus.FORBIDDEN,
            "Only an instance administrator can reset this for another instance administrator"),

    /** A member's second factor a station administrator asked to clear that could not be cleared. */
    SECOND_FACTOR_NOT_RESET_ON_STATION(41, HttpStatus.NOT_FOUND, Sentences.SECOND_FACTOR_NOT_RESET),

    /** A second factor proved at the sign-in step far more often than a person could. */
    TWO_FACTOR_CODE_TOO_OFTEN(42, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A second factor proved again for a sensitive action far more often than a person could. */
    TWO_FACTOR_STEP_UP_TOO_OFTEN(43, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A security key used to sign in far more often than a person could. */
    SECURITY_KEY_SIGN_IN_TOO_OFTEN(44, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A security key used to confirm a sensitive action far more often than a person could. */
    SECURITY_KEY_STEP_UP_TOO_OFTEN(45, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Another device asked to confirm a sensitive action far more often than a person could. */
    STEP_UP_DEVICE_REQUEST_TOO_OFTEN(46, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Waiting on another device's confirmation, asked after far more often than a screen would. */
    STEP_UP_DEVICE_POLL_TOO_OFTEN(47, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A sensitive action confirmed with the password far more often than a person could. */
    STEP_UP_PASSWORD_TOO_OFTEN(48, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A sensitive action confirmed with a passkey far more often than a person could. */
    STEP_UP_PASSKEY_TOO_OFTEN(49, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A session that another device vouched for, trying to vouch for a further one. */
    VOUCHED_SESSION_CANNOT_VOUCH(
            50,
            HttpStatus.FORBIDDEN,
            "A sign-in another device confirmed cannot confirm a further device. Use a device you signed in on yourself");

    private final Definition definition;

    TwoFactorRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.TWO_FACTOR, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
