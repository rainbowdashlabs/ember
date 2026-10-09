/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#STATIONS}: stations.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum StationRefusal implements Refusal {
    /** A confirmation link for an application that has been used, has run out, or was never one. */
    STATION_APPLICATION_LINK_UNKNOWN(1, HttpStatus.NOT_FOUND, "That confirmation link is no longer good"),

    /** An application to join a station that is gone. */
    STATION_APPLICATION_NOT_HERE(2, HttpStatus.NOT_FOUND, Sentences.STATION_APPLICATION_NOT_HERE),

    /** An application that was gone or already answered when its acceptance was asked for. */
    STATION_APPLICATION_NOT_HERE_ON_ACCEPTANCE(3, HttpStatus.NOT_FOUND, Sentences.STATION_APPLICATION_NOT_HERE),

    /** An application that was gone or already answered when its denial was asked for. */
    STATION_APPLICATION_NOT_HERE_ON_DENIAL(4, HttpStatus.NOT_FOUND, Sentences.STATION_APPLICATION_NOT_HERE),

    /**
     * A public address another station on this instance already answers at. Named because a screen
     * has to tell this one refusal apart from every other refusal a settings form can get, and
     * matching on the English sentence to do it broke the moment anybody reworded it.
     */
    STATION_SLUG_TAKEN(5, HttpStatus.CONFLICT, "Another station is already reached at that address"),

    /** The station whose settings were opened, gone between signing in and reading them. */
    STATION_NOT_HERE_ON_MANAGE(6, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A change to a station's settings that would leave it without a name. */
    STATION_NEEDS_A_NAME_ON_CHANGE(7, HttpStatus.BAD_REQUEST, Sentences.STATION_NEEDS_A_NAME),

    /** A time zone chosen for a station that this instance cannot place. */
    STATION_TIME_ZONE_NOT_KNOWN(
            8, HttpStatus.BAD_REQUEST, "That is not a time zone this instance knows, so nothing was saved"),

    /** A public address for a station that could not be taken, for a reason of its own. */
    STATION_ADDRESS_NOT_USABLE(9, HttpStatus.BAD_REQUEST, "That public address cannot be used, so nothing was saved"),

    /** A station that went between its settings being written and being read back. */
    STATION_NOT_HERE_AFTER_CHANGE(10, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A logo uploaded at a station whose cluster sets the logo for it. */
    LOGO_SET_BY_CLUSTER(
            11, HttpStatus.BAD_REQUEST, "The cluster this station belongs to sets the logo, so nothing was saved"),

    /** A logo upload that arrived without a picture in it. */
    LOGO_UPLOAD_MISSING_FILE(12, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A logo larger than a station logo may be. */
    LOGO_TOO_LARGE(13, HttpStatus.BAD_REQUEST, "A logo may be at most 2 MB, so nothing was saved"),

    /** A logo in a kind of picture that is not taken here. */
    LOGO_KIND_NOT_TAKEN(
            14, HttpStatus.BAD_REQUEST, "A logo has to be a PNG, JPEG, WebP or GIF picture, so nothing was saved"),

    /** A logo whose bytes could not be read off the upload. */
    LOGO_NOT_READ(15, HttpStatus.BAD_REQUEST, "That file could not be read, so nothing was saved"),

    /** A station whose logo was asked for by name and which is not here. */
    STATION_NOT_HERE_FOR_LOGO(16, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** The times a station has asked its gathered mail to go out at, where it has asked for none. */
    NOTIFICATION_TIMES_NOT_SET(17, HttpStatus.NOT_FOUND, "This station has set no times of its own for notifications"),

    /** A time of day for notifications that cannot be read as one. */
    NOTIFICATION_TIME_NOT_A_TIME(18, HttpStatus.BAD_REQUEST, "That is not a time of day, so nothing was saved"),

    /** An empty list of mail providers saved over a station's own, which is how post stops silently. */
    MAIL_PROVIDER_LIST_EMPTY(
            19,
            HttpStatus.BAD_REQUEST,
            "To stop sending, clear the mail settings rather than saving an empty list, so nothing was changed"),

    /** A place in the list of mail providers that is not a number. */
    MAIL_PROVIDER_POSITION_NOT_A_NUMBER(
            20, HttpStatus.BAD_REQUEST, "That is not a place in the list of mail providers"),

    /** A test mail asked for at a station that has set up no provider to send it. */
    NO_MAIL_PROVIDER_SET(21, HttpStatus.BAD_REQUEST, "This station has no mail provider set up, so nothing was sent"),

    /** The short way out of a station that has not in fact been moved to another instance. */
    STATION_NOT_MOVED(
            22, HttpStatus.BAD_REQUEST, "This station has not been moved to another instance, so nothing was deleted"),

    /** A station handed over by somebody who does not own it. */
    ONLY_THE_OWNER_HANDS_OVER(24, HttpStatus.FORBIDDEN, "Only the owner of this station can hand it over"),

    /** A station handed to somebody who does not manage it. */
    NEW_OWNER_NOT_A_MANAGER(
            25, HttpStatus.BAD_REQUEST, "Only a manager of this station can be made its owner, so nothing was changed"),

    /** An import started without the transfer code that says what is being fetched. */
    IMPORT_NEEDS_A_TRANSFER_CODE(
            26, HttpStatus.BAD_REQUEST, "Give the transfer code of the instance you are moving from"),

    /** A transfer code this instance cannot read or is no longer carrying out. */
    TRANSFER_CODE_NOT_GOOD_ON_IMPORT(27, HttpStatus.BAD_REQUEST, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** A transfer code that names no instance, sent without one alongside it. */
    IMPORT_NEEDS_A_SOURCE(
            28,
            HttpStatus.BAD_REQUEST,
            "That transfer code names no instance to fetch from, so give the address as well"),

    /** The progress of an import at a station where none is running. */
    NO_IMPORT_RUNNING(29, HttpStatus.NOT_FOUND, "No import is running for this station"),

    /** A deletion confirmed by a link that carried nothing to confirm with. */
    STATION_DELETE_LINK_CARRIES_NOTHING(30, HttpStatus.BAD_REQUEST, Sentences.LINK_CARRIES_NOTHING),

    /** A confirmation link for a deletion that has been used, has run out, or was never one. */
    STATION_DELETE_LINK_UNKNOWN(
            31, HttpStatus.BAD_REQUEST, "That confirmation link has been used or has run out, so nothing was deleted"),

    /** A block lifted for something this instance does not know as a mail provider. */
    MAIL_PROVIDER_NOT_KNOWN(32, HttpStatus.BAD_REQUEST, "That is not a mail provider this instance knows"),

    /** A station being made without a name. */
    STATION_NEEDS_A_NAME(33, HttpStatus.BAD_REQUEST, Sentences.STATION_NEEDS_A_NAME),

    /** A change to a station that would leave it without a name. */
    STATION_NEEDS_A_NAME_ON_UPDATE(34, HttpStatus.BAD_REQUEST, Sentences.STATION_NEEDS_A_NAME),

    /** A station that went before its name and its manager could be written. */
    STATION_NOT_HERE_ON_MANAGER_UPDATE(35, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A station that went before its new name could be written. */
    STATION_NOT_HERE_ON_UPDATE(36, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A station that was already gone when its deletion was asked for. */
    STATION_NOT_DELETED(37, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A station asked for by name that is not here. */
    STATION_NOT_HERE_BY_NAME(38, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Something in the address that has to name a station and does not. */
    STATION_NAME_NOT_READABLE(39, HttpStatus.BAD_REQUEST, "That does not name a station"),

    /** An application to join sent to an instance that is taking none. */
    STATION_REGISTRATION_SWITCHED_OFF(
            40, HttpStatus.FORBIDDEN, "This instance is not taking applications for new stations at the moment"),

    /** An application to join that left one of its fields empty. */
    STATION_APPLICATION_NEEDS_EVERY_FIELD(
            41,
            HttpStatus.BAD_REQUEST,
            "Every field is needed: your name, your email address and the name of the station, "
                    + "so nothing was saved"),

    /** An application confirmed by a link that carried nothing to confirm with. */
    STATION_APPLICATION_LINK_CARRIES_NOTHING(42, HttpStatus.BAD_REQUEST, Sentences.LINK_CARRIES_NOTHING),

    /** An application that could not be turned into a station, whose own words stay in the log. */
    STATION_APPLICATION_NOT_ACCEPTED(
            43,
            HttpStatus.BAD_REQUEST,
            "This application could not be accepted, so nothing was saved: it may already have been "
                    + "answered, or a role the new station needs is missing here"),

    /** An application turned down that is no longer waiting for an answer. */
    STATION_APPLICATION_NOT_DENIED(
            44,
            HttpStatus.BAD_REQUEST,
            "This application is not waiting to be answered any more, so nothing was saved"),

    /**
     * A station asked about from the open web that has nothing to show there. One code for all four
     * ways of getting here deliberately: an address nobody answers at, a station that has put
     * nothing on the open web, an association's own station, and one whose wiki is switched off all
     * say the same thing, because telling them apart would let a stranger learn which stations exist
     * and which of them are merely hidden.
     */
    PUBLIC_STATION_NOTHING_TO_SHOW(45, HttpStatus.NOT_FOUND, "There is nothing here for the open web to see"),

    /** A first station asked for on an instance that already has one; further stations are made elsewhere. */
    FIRST_STATION_ALREADY_FOUNDED(46, HttpStatus.CONFLICT, "This instance already has a station, nothing was created"),

    /** A first station asked for without a name. */
    FIRST_STATION_NEEDS_A_NAME(47, HttpStatus.BAD_REQUEST, Sentences.STATION_NEEDS_A_NAME),

    /** A station's mail settings tested with a recipient that is plainly not an address. */
    STATION_TEST_MAIL_RECIPIENT_NOT_AN_ADDRESS(
            48, HttpStatus.BAD_REQUEST, "That is not an email address, so nothing was sent"),

    /** A station location saved with no location in the request at all. */
    STATION_LOCATION_MISSING(49, HttpStatus.BAD_REQUEST, "Give the location, so nothing was saved"),

    /** A station location given one coordinate without the other. */
    STATION_LOCATION_HALF_PINNED(
            50, HttpStatus.BAD_REQUEST, "Give both the latitude and the longitude, or neither, so nothing was saved"),

    /** A station location with a latitude north of the north pole or south of the south pole. */
    STATION_LATITUDE_OUT_OF_RANGE(
            51, HttpStatus.BAD_REQUEST, "The latitude has to lie between -90 and 90, so nothing was saved"),

    /** A station location with a longitude beyond the date line either way. */
    STATION_LONGITUDE_OUT_OF_RANGE(
            52, HttpStatus.BAD_REQUEST, "The longitude has to lie between -180 and 180, so nothing was saved"),

    /** A station location whose country is not written as a two-letter country code. */
    STATION_COUNTRY_NOT_A_CODE(
            53,
            HttpStatus.BAD_REQUEST,
            "Give the country as its two capital letters, such as DE, so nothing was saved"),

    /** A station location whose address line is longer than a location keeps. */
    STATION_ADDRESS_LINE_TOO_LONG(
            54, HttpStatus.BAD_REQUEST, "The street and number are too long, so nothing was saved"),

    /** A station location whose postal code is longer than a location keeps. */
    STATION_POSTAL_CODE_TOO_LONG(55, HttpStatus.BAD_REQUEST, "The postal code is too long, so nothing was saved"),

    /** A station location whose city is longer than a location keeps. */
    STATION_CITY_TOO_LONG(56, HttpStatus.BAD_REQUEST, "The city is too long, so nothing was saved"),

    /** The location of a station that is not here, asked for to show it. */
    STATION_NOT_HERE_FOR_LOCATION(57, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** An archive imported into a cluster's home station, which holds what its cluster owns. */
    STATION_IMPORT_INTO_CLUSTER_HOME(
            58, HttpStatus.BAD_REQUEST, "A cluster's home station cannot be imported into, so nothing was changed"),

    /** An archive imported over a station that belongs to a cluster. */
    STATION_IMPORT_INTO_CLUSTER_MEMBER(
            59,
            HttpStatus.BAD_REQUEST,
            "A station that belongs to a cluster cannot be overwritten from an archive, so nothing was changed"),

    /** A station brought over from another instance whose answer carried no station at all. */
    STATION_IMPORT_SOURCE_HAS_NO_STATION(
            60, HttpStatus.BAD_REQUEST, "The other instance sent no station to bring over, so nothing was imported"),

    /**
     * The caller's own station, gone after the first page of an import from another instance was
     * already written into it and before the rest could start.
     */
    STATION_IMPORT_TARGET_NOT_HERE(
            61,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The station the import was to go into is not here any more, so nothing was imported"),

    /** A failed import tried again for a station no import is known for. */
    STATION_IMPORT_NOTHING_TO_RETRY(
            62, HttpStatus.NOT_FOUND, "No import is known for that station, so there is nothing to retry"),

    /** An import tried again that has not failed. */
    STATION_IMPORT_NOT_FAILED(63, HttpStatus.CONFLICT, "Only an import that failed can be tried again"),

    /** An import from an address that is not a public HTTPS address. */
    STATION_IMPORT_SOURCE_NOT_PUBLIC(64, HttpStatus.BAD_REQUEST, Sentences.FEDERATION_ADDRESS_NOT_PUBLIC),

    /** An import from another instance that could not tell which version it runs. */
    STATION_IMPORT_SOURCE_NOT_READ(
            65,
            HttpStatus.BAD_REQUEST,
            "The other instance could not be asked which version it runs, so nothing was imported. "
                    + "The reason is in the instance log"),

    /** An import from an instance too old to say which version it runs. */
    STATION_IMPORT_SOURCE_TOO_OLD(
            66,
            HttpStatus.BAD_REQUEST,
            "The other instance is too old to bring a station over from, so nothing was imported. "
                    + "Update it first"),

    /** An import from an instance that runs a different version than this one. */
    STATION_IMPORT_SCHEMA_DIFFERS(
            67,
            HttpStatus.BAD_REQUEST,
            "The two instances do not run the same version, so nothing was imported. "
                    + "Update the older one and try again"),

    /** A cluster's home station put up to move to another instance. */
    STATION_TRANSFER_OF_CLUSTER_HOME(
            68, HttpStatus.BAD_REQUEST, "A cluster's home station cannot be moved to another instance"),

    /** A station that belongs to a cluster put up to move to another instance. */
    STATION_TRANSFER_OF_CLUSTER_MEMBER(
            69,
            HttpStatus.BAD_REQUEST,
            "A station that belongs to a cluster cannot be moved to another instance. Leave the cluster first"),

    /** An onboarding task ticked off that no task of that level is called. */
    ONBOARDING_TASK_UNKNOWN(70, HttpStatus.BAD_REQUEST, "That is not a step of getting started"),

    /** An onboarding task ticked off by hand that finishes itself once it is really done. */
    ONBOARDING_TASK_FINISHES_ITSELF(
            71, HttpStatus.BAD_REQUEST, "This step ticks itself off once it is actually done, so nothing was changed"),

    /** A station's onboarding task marked by a member who is not here any more. */
    ONBOARDING_MEMBER_NOT_HERE(72, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A station whose look was saved, gone before the save reached it. */
    STATION_NOT_HERE_FOR_LOOK(73, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** An address for replies to the station's mail that is plainly not an address. */
    MAIL_REPLY_TO_NOT_AN_ADDRESS(
            74, HttpStatus.BAD_REQUEST, "That is not an email address, so the reply address was not saved"),

    /** A failed import into a station that was here before, which a retry would have to delete. */
    STATION_IMPORT_INTO_NOT_RETRIED(
            75,
            HttpStatus.CONFLICT,
            "An import into an existing station cannot be tried again here, since that would remove the station"),

    /**
     * A partner's request to a station that moved to another installation, reaching the copy it left
     * here. The detail names the installation it moved to, where one is known.
     */
    STATION_MOVED_AWAY(80, HttpStatus.GONE, "This station has moved to another installation"),

    /** A station brought back to the installation that still holds the copy it left when it moved away. */
    STATION_IMPORT_MOVED_AWAY_COPY_HERE(
            81,
            HttpStatus.CONFLICT,
            "The copy this station left here when it moved away is still here, so nothing was imported. "
                    + "Delete that copy first");

    private final Definition definition;

    StationRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.STATIONS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
