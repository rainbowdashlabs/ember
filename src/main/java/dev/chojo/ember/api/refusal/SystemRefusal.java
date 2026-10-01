/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#SYSTEM}: the instance itself.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum SystemRefusal implements Refusal {
    /** A language asked for in a path that is not written the way a language is. */
    SETTINGS_LOCALE_NOT_A_LANGUAGE(1, HttpStatus.BAD_REQUEST, Sentences.LOCALE_NOT_GOOD),

    /** A language whose folder of texts would sit outside the one the instance keeps them in. */
    SETTINGS_LOCALE_OUT_OF_PLACE(2, HttpStatus.BAD_REQUEST, Sentences.LOCALE_NOT_GOOD),

    /** The same check where the folder is resolved, for a caller that came past the first one. */
    SETTINGS_LOCALE_FOLDER_OUT_OF_PLACE(3, HttpStatus.BAD_REQUEST, Sentences.LOCALE_NOT_GOOD),

    /** A setting given a number outside the range it allows. */
    SETTING_OUT_OF_RANGE(
            4, HttpStatus.BAD_REQUEST, "That setting was given a number outside what it allows, so nothing was saved"),

    /** A language chosen for outgoing mail that no mail is written in here. */
    NO_MAIL_WRITTEN_IN_THAT_LANGUAGE(
            5, HttpStatus.BAD_REQUEST, "No mail is written in that language on this instance, so nothing was saved"),

    /** A session on an untrusted device set to last longer than one on a trusted device. */
    UNTRUSTED_SESSION_OUTLASTS_TRUSTED(
            6,
            HttpStatus.BAD_REQUEST,
            "An untrusted device may not keep a session longer than a trusted one, so nothing was saved"),

    /** A fresh pepper asked for where one is already set, which would lock every session out. */
    TOKEN_PEPPER_ALREADY_SET(
            7, HttpStatus.BAD_REQUEST, "auth.tokenPepper is already set, and it is not replaced from here"),

    /** The password leak check saved without the address of the service that answers it. */
    PASSWORD_LEAK_CHECK_NEEDS_AN_ADDRESS(
            8, HttpStatus.BAD_REQUEST, "Give the address of the service that checks passwords against known leaks"),

    /** A fresh second-factor key asked for where one is already set. */
    TWO_FACTOR_SECRET_KEY_ALREADY_SET(
            9, HttpStatus.BAD_REQUEST, "The second-factor secret key is already set, and it is not replaced from here"),

    /** Authenticator settings saved without the name an authenticator app would show. */
    AUTHENTICATOR_NEEDS_AN_ISSUER(
            10,
            HttpStatus.BAD_REQUEST,
            "Give the name an authenticator app should show for this instance, so nothing was saved"),

    /** An authenticator algorithm that is not one of the three an authenticator app can do. */
    AUTHENTICATOR_ALGORITHM_UNKNOWN(
            11, HttpStatus.BAD_REQUEST, "The algorithm has to be SHA1, SHA256 or SHA512, so nothing was saved"),

    /** A security key attestation setting that is not one of the three the standard has. */
    SECURITY_KEY_ATTESTATION_UNKNOWN(
            12, HttpStatus.BAD_REQUEST, "Attestation has to be none, indirect or direct, so nothing was saved"),

    /** A test mail asked for on an instance that has set up nothing to send it with. */
    INSTANCE_HAS_NO_MAIL_PROVIDER(
            13, HttpStatus.BAD_REQUEST, "This instance has no mail provider set up, so nothing was sent"),

    /** A place in the instance list of mail providers that is not a number. */
    INSTANCE_MAIL_PROVIDER_POSITION_NOT_A_NUMBER(
            14, HttpStatus.BAD_REQUEST, "That is not a place in the list of mail providers"),

    /** A block lifted for a kind of mail provider this instance does not know. */
    MAIL_PROVIDER_KIND_UNKNOWN(15, HttpStatus.BAD_REQUEST, "That is not a mail provider this instance knows"),

    /** A level the log is to be kept at that is not one of the levels there are. */
    LOG_LEVEL_UNKNOWN(16, HttpStatus.BAD_REQUEST, "That is not a level the log can be kept at"),

    /** An empty list of mail providers saved over the instance list, which is how post stops silently. */
    INSTANCE_MAIL_PROVIDER_LIST_EMPTY(
            17,
            HttpStatus.BAD_REQUEST,
            "To stop sending, clear the mail settings rather than saving an empty list, so nothing was changed"),

    /** A legal document uploaded in a shape that could not be turned into text. */
    LEGAL_DOCUMENT_NOT_READ(
            18, HttpStatus.BAD_REQUEST, "That file could not be read as a document, so nothing was imported"),

    /** A legal document imported with neither a file nor any text in the request. */
    LEGAL_DOCUMENT_NEEDS_TEXT(
            19,
            HttpStatus.BAD_REQUEST,
            "Give the document to import, either as a file or as text, so nothing was imported"),

    /** A kind of legal document named in a path that is not one this instance keeps. */
    LEGAL_DOCUMENT_KIND_UNKNOWN(20, HttpStatus.BAD_REQUEST, "That is not a kind of legal document kept here"),

    /** The figures for one endpoint asked for without saying which endpoint. */
    ENDPOINT_NOT_NAMED(21, HttpStatus.BAD_REQUEST, "Name the method and the path of the endpoint you want"),

    /** A table written to in the data tracking inspector that it keeps no record of. */
    TRACKED_TABLE_NOT_HERE(22, HttpStatus.NOT_FOUND, Sentences.TRACKED_TABLE_NOT_HERE),

    /** A table whose columns were to be checked and that data tracking keeps no record of. */
    TRACKED_TABLE_NOT_HERE_ON_CHECK(23, HttpStatus.NOT_FOUND, Sentences.TRACKED_TABLE_NOT_HERE),

    /** Installer answers kept without a single answer in them. */
    INSTALL_ANSWERS_MISSING(24, HttpStatus.BAD_REQUEST, "The installer sent no answers to keep, so nothing was kept"),

    /** An install code the installer presented that has run out or was never handed out. */
    INSTALL_CODE_NOT_GOOD(25, HttpStatus.NOT_FOUND, "That code is not one the installer can still fetch answers with"),

    /** A problem report sent without anything written in it. */
    PROBLEM_REPORT_NEEDS_A_MESSAGE(
            26, HttpStatus.BAD_REQUEST, "Write what went wrong before sending the report, so nothing was sent"),

    /** A problem report whose picture was asked for and that is not here. */
    PROBLEM_REPORT_NOT_HERE(27, HttpStatus.NOT_FOUND, "That problem report is not here any more"),

    /** A problem report that was written without a picture. */
    PROBLEM_REPORT_HAS_NO_PICTURE(28, HttpStatus.NOT_FOUND, "That problem report was written without a picture"),

    /** A problem report that names a picture the store no longer holds. */
    PROBLEM_REPORT_PICTURE_NOT_HERE(
            29, HttpStatus.NOT_FOUND, "The picture that went with that report is not here any more"),

    /** A problem in the instance list acknowledged after it had already gone from it. */
    PROBLEM_NOT_HERE(30, HttpStatus.NOT_FOUND, "That problem is not here any more"),

    /**
     * A sitemap asked for a station that is not here, or that puts nothing in public. One code
     * deliberately: the two are told apart by nobody but a stranger counting stations, and this
     * answer is public.
     */
    SITEMAP_NOT_HERE(31, HttpStatus.NOT_FOUND, "There is no sitemap here"),

    /** A table of rows and columns to be read where the upload carried no file. */
    CSV_UPLOAD_MISSING_FILE(32, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** An upload that could not be read as a table of rows and columns. */
    CSV_NOT_READ(33, HttpStatus.BAD_REQUEST, "That file could not be read as a table of rows and columns"),

    /**
     * A settings change whose configuration file could not be written. The change is taken back,
     * so the instance keeps running on what the file says.
     */
    SETTINGS_NOT_SAVED(
            34,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The configuration file could not be written, so the settings were left as they were"),

    /** A look the instance is to start every station from that is not one of the looks on offer. */
    THEME_FEEL_UNKNOWN(35, HttpStatus.BAD_REQUEST, Sentences.LOOK_NOT_OFFERED),

    /** An action the public demo blocks outright by its address. */
    DEMO_BLOCKS_ACTION(36, HttpStatus.BAD_REQUEST, "This is switched off in the demo, so nothing was done"),

    /** An upload to an address the public demo answers reads on but takes no writes at. */
    DEMO_BLOCKS_UPLOAD(37, HttpStatus.BAD_REQUEST, Sentences.DEMO_UPLOADS_OFF),

    /** Creating or deleting a station on the public demo. */
    DEMO_BLOCKS_STATION_MANAGEMENT(
            38,
            HttpStatus.BAD_REQUEST,
            "Adding and removing stations is switched off in the demo, so nothing was changed"),

    /** Changing the roles of a member or a group on the public demo. */
    DEMO_BLOCKS_ROLE_CHANGES(
            39, HttpStatus.BAD_REQUEST, "Changing roles is switched off in the demo, so nothing was changed"),

    /** Setting up a security key on the public demo, which would lock the demo account behind it. */
    DEMO_BLOCKS_SECURITY_KEY_SETUP(
            40, HttpStatus.BAD_REQUEST, "Setting up a security key is switched off in the demo, so nothing was saved"),

    /** Accepting an invite to a station on the public demo. */
    DEMO_BLOCKS_ACCEPTING_INVITES(
            41, HttpStatus.BAD_REQUEST, "Accepting invites is switched off in the demo, so nothing was done"),

    /** Signing up for a public waiting list on the public demo. */
    DEMO_BLOCKS_PUBLIC_WAITING_LIST_SIGN_UP(
            42,
            HttpStatus.BAD_REQUEST,
            "Signing up for a waiting list is switched off in the demo, so nothing was saved"),

    /** A file uploaded to a page on the public demo. */
    DEMO_BLOCKS_PAGE_UPLOADS(43, HttpStatus.BAD_REQUEST, Sentences.DEMO_UPLOADS_OFF),

    /** A folder icon or an article picture uploaded to the knowledge base on the public demo. */
    DEMO_BLOCKS_KB_UPLOADS(44, HttpStatus.BAD_REQUEST, Sentences.DEMO_UPLOADS_OFF),

    /** Probing another instance from the public demo. */
    DEMO_BLOCKS_PEER_PROBES(
            45,
            HttpStatus.BAD_REQUEST,
            "Reaching out to other instances is switched off in the demo, so nothing was done"),

    /** Asking another station to lend gear on the public demo. */
    DEMO_BLOCKS_LENDING(
            46,
            HttpStatus.BAD_REQUEST,
            "Borrowing from other stations is switched off in the demo, so nothing was saved"),

    /** Asking an AI provider for its models on the public demo. */
    DEMO_BLOCKS_AI_CALLS(
            47, HttpStatus.BAD_REQUEST, "Calling AI providers is switched off in the demo, so nothing was done"),

    /** Mail settings tested on the instance with a recipient that is plainly not an address. */
    INSTANCE_TEST_MAIL_RECIPIENT_NOT_AN_ADDRESS(
            48, HttpStatus.BAD_REQUEST, "That is not an email address, so nothing was sent"),

    /** The picture of a problem report that arrived but could not be kept. */
    PROBLEM_PICTURE_NOT_KEPT(
            49, HttpStatus.INTERNAL_SERVER_ERROR, "The picture could not be kept, so the report was not sent"),

    /** The picture of a problem report that does not read as a picture at all. */
    PROBLEM_PICTURE_NOT_READABLE(
            50, HttpStatus.BAD_REQUEST, "The picture could not be read, so the report was not sent"),

    /** The picture of a problem report that was announced and has nothing in it. */
    PROBLEM_PICTURE_EMPTY(51, HttpStatus.BAD_REQUEST, "The picture is empty, so the report was not sent"),

    /** The picture of a problem report larger than a report takes. */
    PROBLEM_PICTURE_TOO_LARGE(52, HttpStatus.BAD_REQUEST, "The picture is too large, so the report was not sent"),

    /** The picture of a problem report that is neither a PNG nor a WebP. */
    PROBLEM_PICTURE_KIND_NOT_TAKEN(
            53, HttpStatus.BAD_REQUEST, "A report only takes a picture as PNG or WebP, so the report was not sent"),

    /** Data tracking changed for a table it keeps no record of. */
    TRACKED_TABLE_NOT_HERE_TO_UPDATE(54, HttpStatus.NOT_FOUND, Sentences.TRACKED_TABLE_NOT_HERE),

    /** Every column of a table marked checked where data tracking keeps no record of the table. */
    TRACKED_TABLE_NOT_HERE_TO_VERIFY(55, HttpStatus.NOT_FOUND, Sentences.TRACKED_TABLE_NOT_HERE);

    private final Definition definition;

    SystemRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.SYSTEM, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
