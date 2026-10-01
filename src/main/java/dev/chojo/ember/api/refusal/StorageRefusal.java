/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#STORAGE}: storage.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum StorageRefusal implements Refusal {
    /**
     * A station's files that could not be carried to the storage it asked for.
     *
     * <p>The cause is deliberately kept out of the sentence and left in the log. Carrying files
     * over opens a connection to an address the request itself names, so saying why it failed
     * would answer "is anything listening there" for any address somebody cares to try.
     */
    STATION_STORAGE_MOVE_NOT_DONE(1, HttpStatus.BAD_REQUEST, Sentences.STORAGE_MOVE_NOT_DONE),

    /** A reachability test asked for at a station that keeps no storage of its own. */
    STATION_KEEPS_NO_STORAGE_OF_ITS_OWN(2, HttpStatus.BAD_REQUEST, "This station keeps no storage of its own to test"),

    /** A session naming a station that is not here any more. */
    STORAGE_STATION_NOT_HERE(4, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A change to a station's storage made by a session that stands for no account. */
    NO_ACCOUNT_BEHIND_STORAGE_CHANGE(5, HttpStatus.FORBIDDEN, Sentences.SESSION_HAS_NO_ACCOUNT),

    /** A station asking to move onto its association's storage while it belongs to no association. */
    STATION_ANSWERS_TO_NO_ASSOCIATION(
            6, HttpStatus.BAD_REQUEST, "This station belongs to no association, so nothing was changed"),

    /** An association that does not keep storage for the stations in it. */
    ASSOCIATION_KEEPS_NO_STORAGE_FOR_STATIONS(
            7,
            HttpStatus.BAD_REQUEST,
            "This association does not keep storage for its stations, so nothing was changed"),

    /** An association that keeps storage for its stations but has named none of its own. */
    ASSOCIATION_KEEPS_NO_STORAGE_OF_ITS_OWN(
            8, HttpStatus.BAD_REQUEST, "This association keeps no storage of its own, so nothing was changed"),

    /** A station picking its own storage where its association has kept that decision. */
    ASSOCIATION_DECIDES_WHERE_FILES_ARE_KEPT(
            9,
            HttpStatus.FORBIDDEN,
            "This station's association decides where its files are kept, so nothing was changed"),

    /** Storage named at an address this instance is not allowed to open a connection to. */
    STORAGE_ADDRESS_NOT_ALLOWED(10, HttpStatus.BAD_REQUEST, "That is not an address this instance may reach"),

    /** Object storage saved without the pair of keys that opens it. */
    STORAGE_KEYS_MISSING(
            11,
            HttpStatus.BAD_REQUEST,
            "Give the access key and the secret key for that storage, so nothing was saved"),

    /** A shared folder saved without the name and password that open it. */
    STORAGE_SIGN_IN_MISSING(
            12, HttpStatus.BAD_REQUEST, "Give the user name and the password for that storage, so nothing was saved"),

    /** Storage reached over a file transfer connection given both a password and a key, or neither. */
    STORAGE_SIGN_IN_AMBIGUOUS(
            13,
            HttpStatus.BAD_REQUEST,
            "Give either a password or a private key for that storage, and only one of the two"),

    /** The instance storage changed without saying what to change it to. */
    INSTANCE_STORAGE_TARGET_MISSING(
            14, HttpStatus.BAD_REQUEST, "Say where the files are to be kept, so nothing was changed"),

    /**
     * The instance files that could not be carried to the storage asked for.
     *
     * <p>Redacted for the same reason as {@link #STATION_STORAGE_MOVE_NOT_DONE}: the address is
     * the caller's own, so a cause would say what answers at it.
     */
    INSTANCE_STORAGE_MOVE_NOT_DONE(15, HttpStatus.BAD_REQUEST, Sentences.STORAGE_MOVE_NOT_DONE),

    /** A change to the instance storage made by a session that stands for no account. */
    NO_ACCOUNT_BEHIND_INSTANCE_STORAGE_CHANGE(16, HttpStatus.FORBIDDEN, Sentences.SESSION_HAS_NO_ACCOUNT),

    /**
     * A move of the instance files that broke while it was being committed.
     *
     * <p>Ours rather than the operator's, unlike the other move refusals: the files had already
     * been prepared and the commit is where it broke, so the move was taken back. The cause stays
     * in the log for the same reason {@link #INSTANCE_STORAGE_MOVE_NOT_DONE} keeps it there.
     */
    INSTANCE_STORAGE_MOVE_TAKEN_BACK(
            17,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The move broke part way through and was taken back, so the files are still where they were. "
                    + "The reason is in the instance log"),

    /**
     * The storage the files of this station are kept on could not be reached: the connection was
     * lost and could not be had again, or the server did not answer in time. Not a fault of Ember's,
     * which is why it is a {@code 503} and says to try again rather than hand out a reference.
     */
    STORAGE_UNREACHABLE(
            18,
            HttpStatus.SERVICE_UNAVAILABLE,
            "The storage of this station cannot be reached right now. Trying again in a moment may work"),

    /** A station named on the storage administration that is not here. */
    STATION_NOT_HERE_FOR_STORAGE_ADMIN(19, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Something named as a station on the storage administration that does not read as one. */
    STATION_NOT_AN_IDENTITY_FOR_STORAGE_ADMIN(20, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_AN_IDENTITY),

    /** The storage history asked for from before something that does not read as a point in time. */
    STORAGE_AUDIT_BEFORE_NOT_A_TIME(
            21, HttpStatus.BAD_REQUEST, "The point in time to list the changes before is not a point in time"),

    /** Files asked for on behalf of a station moving away that is not here any more. */
    STATION_NOT_HERE_FOR_TRANSFER(22, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A file asked for on behalf of a station moving away that is not here any more. */
    TRANSFER_FILE_NOT_HERE(23, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A change at the caller's own station while that station is moving to another instance. */
    STATION_READ_ONLY_FOR_TRANSFER(24, HttpStatus.SERVICE_UNAVAILABLE, Sentences.STATION_BEING_TRANSFERRED),

    /** A change to a station's storage quota or usage by an administrator while that station is moving. */
    STATION_READ_ONLY_FOR_TRANSFER_ON_ADMIN_STORAGE(
            25, HttpStatus.SERVICE_UNAVAILABLE, Sentences.STATION_BEING_TRANSFERRED),

    /** Work outside a request, such as a scheduled job or a partner's call, at a station that is moving. */
    STATION_READ_ONLY_FOR_BACKGROUND_WRITE(26, HttpStatus.SERVICE_UNAVAILABLE, Sentences.STATION_BEING_TRANSFERRED),

    /** A file stored for a station that is moving to another instance. */
    STATION_READ_ONLY_FOR_FILE_WRITE(27, HttpStatus.SERVICE_UNAVAILABLE, Sentences.STATION_BEING_TRANSFERRED),

    /** A file stored while the instance moves all its files to another storage. */
    INSTANCE_STORAGE_MOVING(
            28,
            HttpStatus.SERVICE_UNAVAILABLE,
            "The instance is moving its files to another storage, so nothing was saved. Try again once the move is done"),

    /** The storage of a moving station asked for with a transfer code that is not good. */
    TRANSFER_TOKEN_NOT_GOOD_ON_BACKEND(29, HttpStatus.FORBIDDEN, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** The storage of a moving station asked for a second time, when it is handed over once only. */
    TRANSFER_BACKEND_ALREADY_HANDED_OVER(
            30,
            HttpStatus.GONE,
            "The storage of this move has already been handed over, and it is handed over only once"),

    /** The files of a moving station listed with a transfer code that is not good. */
    TRANSFER_TOKEN_NOT_GOOD_ON_FILE_LIST(31, HttpStatus.FORBIDDEN, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** One file of a moving station asked for with a transfer code that is not good. */
    TRANSFER_TOKEN_NOT_GOOD_ON_FILE(32, HttpStatus.FORBIDDEN, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** One file of a moving station asked for without saying which. */
    TRANSFER_FILE_KEY_MISSING(33, HttpStatus.BAD_REQUEST, "Say which file is wanted"),

    /** An account's picture asked for with a transfer code that is not good. */
    TRANSFER_TOKEN_NOT_GOOD_ON_AVATAR(34, HttpStatus.FORBIDDEN, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** An account's picture asked for during a move where the account has none. */
    TRANSFER_AVATAR_NOT_HERE(35, HttpStatus.NOT_FOUND, "That account has no picture to carry over"),

    /** Files of a moving station asked for without saying which kind. */
    TRANSFER_FILE_KIND_MISSING(36, HttpStatus.BAD_REQUEST, "Say which kind of file is wanted"),

    /** Files of a moving station asked for by a kind no instance keeps. */
    TRANSFER_FILE_KIND_UNKNOWN(37, HttpStatus.BAD_REQUEST, "That is not a kind of file this instance keeps"),

    /** Files of a moving station asked for by a kind that belongs to the instance, not to a station. */
    TRANSFER_FILE_KIND_NOT_A_STATIONS(38, HttpStatus.BAD_REQUEST, "That kind of file does not belong to a station"),

    /** Files of a moving station asked for by a kind that does not move with a station. */
    TRANSFER_FILE_KIND_NOT_MOVABLE(39, HttpStatus.BAD_REQUEST, "That kind of file does not move with a station");

    private final Definition definition;

    StorageRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.STORAGE, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
