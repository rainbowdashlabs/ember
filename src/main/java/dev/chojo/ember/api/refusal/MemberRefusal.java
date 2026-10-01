/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#MEMBERS}: members and accounts.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum MemberRefusal implements Refusal {
    /** An avatar asked for by somebody who may not see it, or of somebody who has none. */
    AVATAR_NOT_HERE(1, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** An avatar upload that arrived without the picture itself. */
    AVATAR_UPLOAD_MISSING_FILE(2, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A picture that could not be read as one, or is of a kind not taken here. */
    AVATAR_NOT_A_PICTURE(3, HttpStatus.BAD_REQUEST, "That file could not be taken as a picture"),

    /** An avatar that was read and then refused for what it held. */
    AVATAR_NOT_SAVED(4, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_NOT_SAVED),

    /** An avatar that broke on the way in, which is Ember's to look into rather than the reader's. */
    AVATAR_NOT_PROCESSED(5, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UPLOAD_NOT_PROCESSED),

    /** A session that is signed in to a station but stands for no account. */
    SESSION_HAS_NO_ACCOUNT(6, HttpStatus.BAD_REQUEST, Sentences.SESSION_HAS_NO_ACCOUNT),

    /** A session whose account could not be resolved to the one an avatar is kept under. */
    SESSION_ACCOUNT_NOT_RESOLVED(7, HttpStatus.BAD_REQUEST, Sentences.SESSION_HAS_NO_ACCOUNT),

    /** An account that is not a member of the station the reader is signed in to. */
    MEMBER_NOT_HERE(8, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** An account being sent through joining again that is gone. */
    ACCOUNT_NOT_HERE_ON_ONBOARDING_AGAIN(9, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** An account being given a passkey code that is gone. */
    ACCOUNT_NOT_HERE_ON_PASSKEY_CODE(10, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** An account whose details were to be changed and is gone. */
    ACCOUNT_NOT_HERE_ON_CHANGE(11, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** An account that went between being read and having its details written. */
    MEMBER_NOT_HERE_ON_CHANGE(12, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** An account whose password was to be reset and is gone. */
    ACCOUNT_NOT_HERE_ON_PASSWORD_RESET(13, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** An account the password reset mail could not be sent for. */
    ACCOUNT_NOT_HERE_ON_PASSWORD_RESET_MAIL(14, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** A profile question that went before the change to it could be written. */
    PROFILE_FIELD_NOT_HERE_ON_CHANGE(16, HttpStatus.NOT_FOUND, Sentences.PROFILE_FIELD_NOT_HERE),

    /** A profile question that was already gone when its deletion was asked for. */
    PROFILE_FIELD_NOT_HERE_ON_DELETE(17, HttpStatus.NOT_FOUND, Sentences.PROFILE_FIELD_NOT_HERE),

    /** A registration that named no name, no address or no password to sign in with. */
    REGISTRATION_DETAILS_MISSING(
            18, HttpStatus.BAD_REQUEST, "Give a first name, a last name, an address and a password to register"),

    /**
     * A self-registration the sign-up refused, whether over the registration code it named or the
     * address it chose. One code deliberately: an endpoint nobody has signed in to must not say
     * which addresses are already registered here, and telling the reasons apart would.
     */
    REGISTRATION_REFUSED(
            19, HttpStatus.CONFLICT, "You could not be registered with these details, so no account was made"),

    /** A confirmation of an address that arrived with nothing to confirm. */
    EMAIL_VERIFICATION_TOKEN_MISSING(20, HttpStatus.BAD_REQUEST, Sentences.LINK_CARRIES_NOTHING),

    /**
     * A confirmation link that is unknown, has run out, or has already been spent. One code
     * deliberately: telling them apart would say whether an address was ever asked to confirm
     * itself here, which is the thing this endpoint withholds from anybody guessing links.
     */
    EMAIL_VERIFICATION_LINK_NOT_GOOD(
            21, HttpStatus.BAD_REQUEST, "That confirmation link is no longer good, so the address was not confirmed"),

    /** A second confirmation mail asked for without saying where to send it. */
    RESEND_VERIFICATION_ADDRESS_MISSING(22, HttpStatus.BAD_REQUEST, Sentences.ADDRESS_MISSING),

    /** A password being set without the link that allows it, or without the password itself. */
    PASSWORD_SETUP_DETAILS_MISSING(
            23, HttpStatus.BAD_REQUEST, "A link and a password are both needed, so nothing was saved"),

    /** A chosen password shorter than this instance accepts. */
    NEW_PASSWORD_TOO_SHORT(24, HttpStatus.BAD_REQUEST, Sentences.PASSWORD_TOO_SHORT),

    /** A chosen password that is known to have leaked elsewhere. */
    NEW_PASSWORD_BREACHED(25, HttpStatus.BAD_REQUEST, Sentences.PASSWORD_BREACHED),

    /** A setup or reset link that no account answers to. */
    PASSWORD_SETUP_LINK_UNKNOWN(26, HttpStatus.BAD_REQUEST, "That link is not one a password can be set with"),

    /** A setup or reset link that was good and has since run out. */
    PASSWORD_SETUP_LINK_EXPIRED(27, HttpStatus.BAD_REQUEST, "That link has run out, so nothing was saved"),

    /** A password being set on an instance that has stopped giving them out. */
    PASSWORDS_SWITCHED_OFF(28, HttpStatus.FORBIDDEN, "This instance gives out no passwords: a passkey is the way in"),

    /** An address being put on an account without the link that allows it, or without the address. */
    ADDRESS_SETUP_DETAILS_MISSING(
            29, HttpStatus.BAD_REQUEST, "A link and an address are both needed, so nothing was saved"),

    /** A sign-in that was stopped for an address, carried on with something that stands for no such stop. */
    ADDRESS_SETUP_LINK_UNKNOWN(30, HttpStatus.BAD_REQUEST, "That step cannot be carried on with. Sign in again"),

    /** The same stop, carried on with long enough after it that it no longer stands. */
    ADDRESS_SETUP_LINK_EXPIRED(31, HttpStatus.BAD_REQUEST, "That step has run out. Sign in again"),

    /** An address that is not shaped like one. */
    ADDRESS_MALFORMED(32, HttpStatus.BAD_REQUEST, "That is not an address anything can be sent to"),

    /** An address shaped like one that nothing can actually be delivered to. */
    ADDRESS_UNREACHABLE(33, HttpStatus.BAD_REQUEST, "Nothing can be delivered to that address, so nothing was saved"),

    /** An address another account on this instance already carries, given at the sign-in stop. */
    ADDRESS_TAKEN_ON_SETUP(34, HttpStatus.CONFLICT, Sentences.ADDRESS_BELONGS_TO_ANOTHER),

    /** A forgotten password asked about without saying whose. */
    FORGOTTEN_PASSWORD_ADDRESS_MISSING(35, HttpStatus.BAD_REQUEST, Sentences.ADDRESS_MISSING),

    /** A sign-in that left one of the two fields empty. */
    SIGN_IN_DETAILS_MISSING(36, HttpStatus.BAD_REQUEST, "Give both a sign-in name and a password"),

    /**
     * A sign-in that did not work, for every reason a sign-in does not work: no such account, the
     * wrong password, an address nobody has confirmed, an account whose password has been switched
     * off. One code and one sentence deliberately, because that is the whole point of this line:
     * anybody trying addresses at this endpoint must be told the same thing every time, or the
     * endpoint becomes a list of who has an account here.
     */
    SIGN_IN_REFUSED(
            37,
            HttpStatus.UNAUTHORIZED,
            "Signing in did not work. Check what you typed, and whether your address is confirmed"),

    /** A quick sign-in on a demo instance that said nobody to sign in as. */
    DEMO_SIGN_IN_ADDRESS_MISSING(38, HttpStatus.BAD_REQUEST, Sentences.ADDRESS_MISSING),

    /** A quick sign-in that did not work, on the demo instances where one is offered at all. */
    DEMO_SIGN_IN_REFUSED(39, HttpStatus.UNAUTHORIZED, "That quick sign-in did not work"),

    /** A password change that left the old or the new one empty. */
    PASSWORD_CHANGE_DETAILS_MISSING(43, HttpStatus.BAD_REQUEST, "Give both the current password and the new one"),

    /** A new password shorter than this instance accepts. */
    CHANGED_PASSWORD_TOO_SHORT(44, HttpStatus.BAD_REQUEST, Sentences.PASSWORD_TOO_SHORT),

    /** A new password that is known to have leaked elsewhere. */
    CHANGED_PASSWORD_BREACHED(45, HttpStatus.BAD_REQUEST, Sentences.PASSWORD_BREACHED),

    /** A password change on an account that has never had one. */
    ACCOUNT_HAS_NO_PASSWORD(
            46,
            HttpStatus.BAD_REQUEST,
            "This account carries no password to change. Sign in with your passkey and set one"),

    /** A password change where the password given as the current one is not it. */
    CURRENT_PASSWORD_WRONG(47, HttpStatus.BAD_REQUEST, "The current password is not right, so nothing was saved"),

    /** An address change being confirmed with nothing to confirm. */
    EMAIL_CHANGE_TOKEN_MISSING(48, HttpStatus.BAD_REQUEST, Sentences.LINK_CARRIES_NOTHING),

    /** An address change onto an address another account has taken meanwhile. */
    EMAIL_CHANGE_ADDRESS_TAKEN(49, HttpStatus.CONFLICT, Sentences.ADDRESS_BELONGS_TO_ANOTHER),

    /**
     * A link confirming an address change that is unknown or has run out. One code deliberately,
     * for the same reason the confirmation of a new address has one.
     */
    EMAIL_CHANGE_LINK_NOT_GOOD(
            50, HttpStatus.BAD_REQUEST, "That confirmation link is no longer good, so the address was not changed"),

    /** A member named by the identifier a member menu hands over, who is gone or has left. */
    MEMBER_NOT_HERE_BY_UID(52, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A membership being made without saying whose account it is for. */
    MEMBER_ACCOUNT_NOT_NAMED(53, HttpStatus.BAD_REQUEST, "Name the account to add as a member"),

    /** A member who cannot yet be written off the register. */
    MEMBER_NOT_MARKED_FORMER(
            55,
            HttpStatus.BAD_REQUEST,
            "This member cannot be marked as having left: they may still hold equipment, or a role"),

    /** An invitation being sent again to a member who has no account behind them. */
    MEMBER_HAS_NO_ACCOUNT(56, HttpStatus.BAD_REQUEST, "This member has no account to write to"),

    /** The account behind a member, gone between the member being read and the mail being sent. */
    ACCOUNT_NOT_HERE_ON_SETUP_MAIL(57, HttpStatus.BAD_REQUEST, Sentences.ACCOUNT_NOT_HERE),

    /** An invitation being sent again to somebody who has already taken their account over. */
    ACCOUNT_ALREADY_SET_UP(58, HttpStatus.BAD_REQUEST, "This account is already set up, so no invitation was sent"),

    /** An invitation for an account that nothing can be delivered about, to anybody. */
    ACCOUNT_NOBODY_TO_WRITE_TO(
            59, HttpStatus.BAD_REQUEST, "Nobody can be written to about this account, so no invitation was sent"),

    /** A member's kind being set without saying which kind. */
    MEMBER_USER_TYPE_NOT_NAMED(60, HttpStatus.BAD_REQUEST, "Name the kind of member this should be"),

    /** A member's joining date being set without saying which date. */
    MEMBER_JOIN_DATE_NOT_NAMED(61, HttpStatus.BAD_REQUEST, "Name the date this member joined"),

    /** An account that administers the instance, acted on from a permission below that. */
    ACCOUNT_ABOVE_YOU(
            62,
            HttpStatus.FORBIDDEN,
            "An account that administers the instance can only be acted on by one that does too"),

    /** A fresh way in being made for somebody, without saying whose account. */
    ACCOUNT_NOT_NAMED_ON_ONBOARDING_AGAIN(63, HttpStatus.BAD_REQUEST, Sentences.ACCOUNT_NOT_NAMED),

    /** A passkey code being handed out, without saying whose account it is for. */
    ACCOUNT_NOT_NAMED_ON_PASSKEY_CODE(64, HttpStatus.BAD_REQUEST, Sentences.ACCOUNT_NOT_NAMED),

    /** A passkey code asked for on behalf of somebody who can be written to directly. */
    MEMBER_HAS_OWN_ADDRESS(65, HttpStatus.FORBIDDEN, "This member has an address of their own: the way in is by mail"),

    /** Somebody else's account being changed by a reader who may only change their own. */
    ACCOUNT_NOT_YOURS_TO_CHANGE(66, HttpStatus.FORBIDDEN, "Changing another account takes the right to edit members"),

    /** An address change onto an address another account already carries. */
    ACCOUNT_ADDRESS_TAKEN(67, HttpStatus.CONFLICT, Sentences.ADDRESS_BELONGS_TO_ANOTHER),

    /** An invitation with nobody's name on it. */
    INVITE_NAME_MISSING(68, HttpStatus.BAD_REQUEST, "Give a first name and a last name"),

    /**
     * An invitation the provisioning refused, whether because the address is spoken for or because
     * the station would not take the membership. One code: the reader is invited to correct the
     * row in front of them, and which half refused does not change what they do about it.
     */
    MEMBER_NOT_PROVISIONED(69, HttpStatus.CONFLICT, "That member could not be set up, so nothing was saved"),

    /** A password reset asked for without saying whose account. */
    ACCOUNT_NOT_NAMED_ON_PASSWORD_RESET(70, HttpStatus.BAD_REQUEST, Sentences.ACCOUNT_NOT_NAMED),

    /** A profile question being made with no name or no kind. */
    PROFILE_FIELD_DETAILS_MISSING_ON_CREATE(71, HttpStatus.BAD_REQUEST, Sentences.PROFILE_FIELD_DETAILS_MISSING),

    /** A profile question being put to an audience named as both a kind of member and a group, or as neither. */
    PROFILE_FIELD_AUDIENCE_AMBIGUOUS_ON_ASSIGN(72, HttpStatus.BAD_REQUEST, Sentences.PROFILE_FIELD_AUDIENCE_AMBIGUOUS),

    /** The same, where an audience is being stopped from being asked rather than started. */
    PROFILE_FIELD_AUDIENCE_AMBIGUOUS_ON_UNASSIGN(
            73, HttpStatus.BAD_REQUEST, Sentences.PROFILE_FIELD_AUDIENCE_AMBIGUOUS),

    /** A profile question being changed to have no name or no kind. */
    PROFILE_FIELD_DETAILS_MISSING_ON_CHANGE(74, HttpStatus.BAD_REQUEST, Sentences.PROFILE_FIELD_DETAILS_MISSING),

    /** Profile questions being put in order without saying whose order it is. */
    PROFILE_FIELD_ORDER_AUDIENCE_MISSING(75, HttpStatus.BAD_REQUEST, "Name the kind of member this order is for"),

    /** A group being made without a name. */
    GROUP_NAME_MISSING_ON_CREATE(76, HttpStatus.BAD_REQUEST, Sentences.GROUP_NAME_MISSING),

    /** A group that went between the ownership check and being read. */
    GROUP_NOT_HERE_ON_READ(77, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE),

    /** A group being renamed to nothing. */
    GROUP_NAME_MISSING_ON_CHANGE(78, HttpStatus.BAD_REQUEST, Sentences.GROUP_NAME_MISSING),

    /** A group that went before the change to it could be written. */
    GROUP_NOT_HERE_ON_CHANGE(79, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE),

    /** A group that was already gone when its deletion was asked for. */
    GROUP_NOT_HERE_ON_DELETE(80, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE),

    /** A tag being made without a name. */
    TAG_NAME_MISSING_ON_CREATE(81, HttpStatus.BAD_REQUEST, Sentences.TAG_NAME_MISSING),

    /** A tag being renamed to nothing. */
    TAG_NAME_MISSING_ON_CHANGE(82, HttpStatus.BAD_REQUEST, Sentences.TAG_NAME_MISSING),

    /** A tag that went before the change to it could be written. */
    MEMBER_TAG_NOT_HERE_ON_CHANGE(83, HttpStatus.NOT_FOUND, Sentences.MEMBER_TAG_NOT_HERE),

    /** A tag that was already gone when its deletion was asked for. */
    MEMBER_TAG_NOT_HERE_ON_DELETE(84, HttpStatus.NOT_FOUND, Sentences.MEMBER_TAG_NOT_HERE),

    /** The list of what a station transfer carries, asked for with a transfer that is over. */
    TRANSFER_TOKEN_NOT_GOOD_ON_TABLES(85, HttpStatus.FORBIDDEN, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** One part of a station transfer, asked for with a transfer that is over. */
    TRANSFER_TOKEN_NOT_GOOD_ON_TABLE(86, HttpStatus.FORBIDDEN, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** A part of a station transfer asked for by a name no transfer carries. */
    TRANSFER_PART_UNKNOWN(87, HttpStatus.BAD_REQUEST, "That is not one of the parts a station transfer carries"),

    /** A transfer being called off that is over already. */
    TRANSFER_TOKEN_NOT_GOOD_ON_ABORT(88, HttpStatus.FORBIDDEN, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** A transfer being signed off that is over already. */
    TRANSFER_TOKEN_NOT_GOOD_ON_COMPLETE(89, HttpStatus.FORBIDDEN, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** A station being taken in without the code the other instance handed out. */
    TRANSFER_TOKEN_MISSING(90, HttpStatus.BAD_REQUEST, "Give the transfer code, so nothing was started"),

    /** A transfer code that nothing can be read out of. */
    TRANSFER_TOKEN_UNREADABLE(
            91, HttpStatus.BAD_REQUEST, "That transfer code could not be read, so nothing was started"),

    /** A transfer code that carries no instance to fetch from, where none was named beside it. */
    TRANSFER_SOURCE_MISSING(
            92, HttpStatus.BAD_REQUEST, "That transfer code names no instance to fetch from, so name one yourself"),

    /** Progress asked about for a station nothing is being taken in for. */
    TRANSFER_IMPORT_NOT_RUNNING(93, HttpStatus.NOT_FOUND, "No transfer into this instance is running for that station"),

    /** A batch of invitations with nobody in it. */
    INVITES_MISSING(94, HttpStatus.BAD_REQUEST, "Name at least one person to invite"),

    /** An invitation naming a kind of member this instance has none of. */
    INVITE_USER_TYPE_UNKNOWN(95, HttpStatus.BAD_REQUEST, "That is not a kind of member anybody can be invited as"),

    /** One row of a batch of invitations with a name or an address left out. */
    INVITE_ENTRY_DETAILS_MISSING(96, HttpStatus.BAD_REQUEST, "Every invitation needs both names and an address"),

    /** A guardian on a row of a batch of invitations with a name or an address left out. */
    INVITE_GUARDIAN_DETAILS_MISSING(97, HttpStatus.BAD_REQUEST, "Every guardian needs both names and an address"),

    /** A registration code being made without the text people would type. */
    REGISTRATION_CODE_TEXT_MISSING(98, HttpStatus.BAD_REQUEST, "Give the registration code its text"),

    /**
     * A registration code that went between the list being drawn and it being opened, or one of
     * another station. One code for both, so a number does not say that a code exists elsewhere.
     */
    REGISTRATION_CODE_NOT_HERE(99, HttpStatus.NOT_FOUND, Sentences.REGISTRATION_CODE_NOT_HERE),

    /**
     * A registration code that was already gone when its deletion was asked for, or one of another
     * station. One code for both, for the same reason as opening one.
     */
    REGISTRATION_CODE_NOT_DELETED(100, HttpStatus.NOT_FOUND, Sentences.REGISTRATION_CODE_NOT_HERE),

    /** A saved filter being kept without a name, a list or anything to filter by. */
    SAVED_FILTER_DETAILS_MISSING(
            101, HttpStatus.BAD_REQUEST, "Give the saved filter a name and something to filter by"),

    /** A saved filter that was already gone when its deletion was asked for. */
    SAVED_FILTER_NOT_HERE_ON_DELETE(102, HttpStatus.NOT_FOUND, "That saved filter is not here any more"),

    /** A column selection being kept under no name. */
    MEMBER_TABLE_PRESET_NAME_MISSING(103, HttpStatus.BAD_REQUEST, "A saved selection needs a name"),

    /** A member list that could not be drawn as a sheet to print. */
    MEMBER_TABLE_NOT_A_SHEET(104, HttpStatus.BAD_REQUEST, "This list could not be turned into a sheet"),

    /** The station a member list is headed with, gone between the two reads. */
    STATION_NOT_HERE_FOR_MEMBER_TABLE(105, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** An import that arrived without the file to read members out of. */
    IMPORT_FILE_MISSING(106, HttpStatus.BAD_REQUEST, "The import arrived without a file in it"),

    /** An import file bigger than one request carries. */
    IMPORT_FILE_TOO_LARGE(107, HttpStatus.BAD_REQUEST, "That file is larger than an import takes"),

    /**
     * A member whose change history was asked about and who is not here. Answered the same for a
     * member of another station, so that a member id cannot be probed for existence from outside.
     */
    MEMBER_NOT_HERE_ON_CHANGE_HISTORY(108, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** Changes to somebody a guardian does not look after. */
    MEMBER_CHANGES_NOT_YOURS(109, HttpStatus.FORBIDDEN, "You may only see the members you look after"),

    /** A change being acknowledged that is gone. */
    PROFILE_FIELD_CHANGE_NOT_HERE(110, HttpStatus.NOT_FOUND, "That change is not here any more"),

    /** Somebody acted on by a guardian who does not look after them. */
    MEMBER_NOT_YOURS_TO_LOOK_AFTER(111, HttpStatus.FORBIDDEN, "You do not look after this member"),

    /** A looked-after member whose profile was asked for and who is gone. */
    MEMBER_NOT_HERE_ON_MANAGED_PROFILE(112, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A looked-after member who went between the profile being opened and the answers being written. */
    MEMBER_NOT_HERE_ON_MANAGED_PROFILE_CHANGE(113, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A looked-after member whose equipment list was asked for and who is gone. */
    MEMBER_NOT_HERE_ON_MANAGED_EQUIPMENT(114, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** An account being deleted that still administers a station. */
    ACCOUNT_STILL_ADMINISTERS_STATION(
            115,
            HttpStatus.BAD_REQUEST,
            "This account still administers a station, so it cannot be deleted. Hand the station on first"),

    /** A password set for a looked-after member that is shorter than this instance takes. */
    MANAGED_PASSWORD_TOO_SHORT(116, HttpStatus.BAD_REQUEST, Sentences.PASSWORD_TOO_SHORT),

    /** A password set for a looked-after member that is known to have leaked. */
    MANAGED_PASSWORD_BREACHED(117, HttpStatus.BAD_REQUEST, Sentences.PASSWORD_BREACHED),

    /** A password set for a looked-after member at an instance that signs in without passwords. */
    MANAGED_PASSWORD_NOT_TAKEN(
            118, HttpStatus.FORBIDDEN, "This instance signs in without passwords, so none was saved for this member"),

    /** A password set for a looked-after member who has an address and sets their own. */
    MANAGED_MEMBER_SETS_THEIR_OWN_PASSWORD(
            119, HttpStatus.FORBIDDEN, "This member has an address of their own and sets their own password"),

    /**
     * The settings of an expiry date that count days backwards or repeat without a gap: a warning
     * or a reminder a negative number of days before the date, or a repeat every zero days.
     */
    EXPIRY_SETTINGS_OUT_OF_RANGE(120, HttpStatus.BAD_REQUEST, Sentences.EXPIRY_OUT_OF_RANGE),

    /**
     * A change sent with the session cookie but without the token that proves this application sent
     * it. Another site can make a browser send the cookie; it cannot read the token.
     */
    REQUEST_NOT_FROM_THIS_PAGE(
            121,
            HttpStatus.FORBIDDEN,
            "This change did not come from an open page of this site, so nothing was done. Reload the page and try again"),

    /** A sign-in started on another site, which must not be able to sign a browser in here. */
    SIGN_IN_FROM_ANOTHER_SITE(122, HttpStatus.FORBIDDEN, "Signing in only works from this site's own sign-in page"),

    /** A member put into a group from their own page whose user type the group does not take. */
    GROUP_WRONG_USER_TYPE_FOR_MEMBER(123, HttpStatus.BAD_REQUEST, Sentences.GROUP_WRONG_USER_TYPE),

    /** Members added on the group page whose user type the group does not take. */
    GROUP_WRONG_USER_TYPE_ON_ADD(124, HttpStatus.BAD_REQUEST, Sentences.GROUP_WRONG_USER_TYPE),

    /** Two groups of one set chosen for a member at once. */
    GROUP_SET_TWO_CHOSEN(
            125, HttpStatus.BAD_REQUEST, "A member can be in only one group of a set, so nothing was saved"),

    /** Members added to a group while they are in another group of its set, without asking to move them. */
    GROUP_SET_ALREADY_IN(
            126,
            HttpStatus.CONFLICT,
            "Some of these members are already in another group of the same set, so nothing was saved"),

    /** Groups put into one set while some members are in more than one of them. */
    GROUP_SET_MEMBERS_OVERLAP(
            127,
            HttpStatus.CONFLICT,
            "Some members are in more than one of these groups, and a set allows only one, so nothing was saved"),

    /** A group bound to user types that some of its members are not of, without asking to take them out. */
    GROUP_BINDING_EXCLUDES_MEMBERS(
            128, HttpStatus.CONFLICT, "Some members of the group are not of the chosen types, so nothing was saved"),

    /** Member ids sent for a group that belong to another station, or to nobody. */
    GROUP_MEMBER_NOT_HERE(129, HttpStatus.BAD_REQUEST, "Some of these members are not here, so nothing was saved"),

    /** A group chosen for a member that belongs to another station, or is gone. */
    GROUP_NOT_HERE_FOR_MEMBER(130, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE_NOTHING_SAVED),

    /** A member put into a group from their own page that grants more than the caller holds. */
    GROUP_GRANTS_MORE_THAN_YOURS_FOR_MEMBER(131, HttpStatus.FORBIDDEN, Sentences.GROUP_GRANTS_MORE_THAN_YOURS),

    /** Members added on the group page to a group that grants more than the caller holds. */
    GROUP_GRANTS_MORE_THAN_YOURS_ON_ADD(132, HttpStatus.FORBIDDEN, Sentences.GROUP_GRANTS_MORE_THAN_YOURS),

    /** New members invited into a group that grants more than the caller holds. */
    GROUP_GRANTS_MORE_THAN_YOURS_ON_INVITE(133, HttpStatus.FORBIDDEN, Sentences.GROUP_GRANTS_MORE_THAN_YOURS),

    /** New members invited into a group of another station, or one that is gone. */
    INVITE_GROUP_NOT_HERE(134, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE_NOTHING_SAVED),

    /** A group put into a set of another station, or one that is gone. */
    GROUP_SET_NOT_HERE_FOR_GROUP(135, HttpStatus.NOT_FOUND, Sentences.GROUP_SET_NOT_HERE),

    /** A set of groups created without a name. */
    GROUP_SET_NAME_MISSING_ON_CREATE(136, HttpStatus.BAD_REQUEST, Sentences.GROUP_SET_NAME_MISSING),

    /** A set of groups renamed to nothing. */
    GROUP_SET_NAME_MISSING_ON_CHANGE(137, HttpStatus.BAD_REQUEST, Sentences.GROUP_SET_NAME_MISSING),

    /** A set of groups created with a name another set of the station carries. */
    GROUP_SET_NAME_TAKEN_ON_CREATE(138, HttpStatus.CONFLICT, Sentences.GROUP_SET_NAME_TAKEN),

    /** A set of groups renamed to a name another set of the station carries. */
    GROUP_SET_NAME_TAKEN_ON_CHANGE(139, HttpStatus.CONFLICT, Sentences.GROUP_SET_NAME_TAKEN),

    /** A set of groups renamed that is not here, or belongs to another station. */
    GROUP_SET_NOT_HERE_ON_CHANGE(140, HttpStatus.NOT_FOUND, Sentences.GROUP_SET_NOT_HERE),

    /** A set of groups deleted that is not here, or belongs to another station. */
    GROUP_SET_NOT_HERE_ON_DELETE(141, HttpStatus.NOT_FOUND, Sentences.GROUP_SET_NOT_HERE),

    /** A registration code whose groups include one that does not take members. */
    REGISTRATION_CODE_GROUP_WRONG_USER_TYPE(
            142,
            HttpStatus.BAD_REQUEST,
            "Somebody registering with a code becomes a member, and one of these groups does not take members, so nothing was saved"),

    /** A registration code whose groups include one of another station, or one that is gone. */
    REGISTRATION_CODE_GROUP_NOT_HERE(143, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE_NOTHING_SAVED),

    /** The consequences of a type change asked for a type that does not exist. */
    USER_TYPE_UNKNOWN_FOR_CONSEQUENCES(144, HttpStatus.BAD_REQUEST, "That is not a type a member can have"),

    /** The groups of a registration code asked for that is gone or belongs to another station. */
    REGISTRATION_CODE_NOT_HERE_FOR_GROUPS(145, HttpStatus.NOT_FOUND, Sentences.REGISTRATION_CODE_NOT_HERE),

    /** The groups of a registration code changed that is gone or belongs to another station. */
    REGISTRATION_CODE_NOT_HERE_TO_CHANGE_GROUPS(146, HttpStatus.NOT_FOUND, Sentences.REGISTRATION_CODE_NOT_HERE),

    /** A value written under an age, which counts itself from a date and holds none of its own. */
    PROFILE_AGE_TAKES_NO_ANSWER(
            147,
            HttpStatus.BAD_REQUEST,
            "An age is counted from a date and takes no answer of its own, so nothing was saved"),

    /** A profile answer the question does not take, such as an unknown choice or a day that is no date. */
    PROFILE_ANSWER_NOT_ACCEPTED(
            148, HttpStatus.BAD_REQUEST, "That answer does not suit this question, so nothing was saved"),

    /** A profile question given a type the member profile does not offer. */
    PROFILE_FIELD_TYPE_NOT_OFFERED(
            149,
            HttpStatus.BAD_REQUEST,
            "The member profile does not offer that type of question, so nothing was saved"),

    /** Registering far more often from one address than a person could. */
    REGISTERING_TOO_OFTEN(150, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Confirming an email address far more often from one address than a person could. */
    EMAIL_VERIFYING_TOO_OFTEN(151, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Asking for the confirmation mail again far more often than a person could. */
    VERIFICATION_MAIL_TOO_OFTEN(152, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Setting a password through a link far more often from one address than a person could. */
    PASSWORD_SETTING_TOO_OFTEN(153, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Setting a sign-in address through a link far more often from one address than a person could. */
    ADDRESS_SETTING_TOO_OFTEN(154, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Checking whether a password link still holds far more often than a person could. */
    PASSWORD_LINK_CHECKED_TOO_OFTEN(155, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Asking for a password reset far more often than a person could. */
    PASSWORD_RESET_TOO_OFTEN(156, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Signing in with a password far more often than a person could. */
    SIGN_IN_TOO_OFTEN(157, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Changing one's own password far more often than a person could. */
    PASSWORD_CHANGE_TOO_OFTEN(158, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Confirming a change of email address far more often than a person could. */
    EMAIL_CHANGE_CONFIRMED_TOO_OFTEN(159, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A profile answer sent in a shape no answer is written in. */
    PROFILE_ANSWER_NOT_READABLE(
            160, HttpStatus.BAD_REQUEST, "That is not an answer this field can take, so nothing was saved"),

    /** A permission named for a member or a group that no permission answers to. */
    MEMBER_PERMISSION_UNKNOWN(161, HttpStatus.BAD_REQUEST, "That permission does not exist, so nothing was saved"),

    /** A permission handed to a member or a group by somebody who does not hold it themselves. */
    MEMBER_PERMISSION_NOT_YOURS_TO_GRANT(
            162,
            HttpStatus.FORBIDDEN,
            "You cannot hand out a permission you do not hold yourself, so nothing was saved"),

    /** A member taking away one of their own permissions. */
    MEMBER_OWN_PERMISSION_NOT_REMOVABLE(
            163, HttpStatus.FORBIDDEN, "You cannot take away your own permissions, so nothing was saved"),

    /** The permission to administer the station, taken from whoever owns it. */
    MEMBER_OWNER_KEEPS_ADMINISTRATION(
            164,
            HttpStatus.FORBIDDEN,
            "Whoever owns the station keeps the permission to administer it, so nothing was saved"),

    /** Signing in switched on for a member whose account has no address to sign in with. */
    MEMBER_SIGN_IN_NEEDS_AN_ADDRESS(
            165, HttpStatus.BAD_REQUEST, "Signing in needs an email address on the account, so nothing was saved"),

    /** The member somebody is to look after, gone before the guardians were written. */
    MEMBER_NOT_HERE_FOR_GUARDIANS(166, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A guardian given to a member who is neither a member nor a trial member, and so looks after themselves. */
    MEMBER_TYPE_TAKES_NO_GUARDIANS(
            167,
            HttpStatus.BAD_REQUEST,
            "Only members and trial members can be given a guardian, so nothing was saved"),

    /** A passkey code a guardian asked for, for a member with an address of their own. */
    MANAGED_MEMBER_HAS_OWN_ADDRESS(
            168, HttpStatus.FORBIDDEN, "This member has an address of their own: the way in is by mail"),

    /** Signing in switched on for a member in somebody's care, at an instance that has no permission for it. */
    MANAGED_SIGN_IN_PERMISSION_MISSING(
            169, HttpStatus.INTERNAL_SERVER_ERROR, "Signing in cannot be switched on here, so nothing was changed"),

    /** Signing in switched on for a member in somebody's care who has neither an address nor a username. */
    MANAGED_SIGN_IN_NEEDS_A_NAME_OR_ADDRESS(
            170,
            HttpStatus.BAD_REQUEST,
            "Give this member an email address or a username before letting them sign in, so nothing was changed"),

    /** A member in somebody's care who has no account to give access to. */
    MANAGED_MEMBER_HAS_NO_ACCOUNT(171, HttpStatus.BAD_REQUEST, "This member has no account yet"),

    /** The account of a member in somebody's care, gone between the member and the account being read. */
    MANAGED_ACCOUNT_NOT_HERE(172, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** A guardian reaching for the access of a member they do not look after. */
    MANAGED_MEMBER_NOT_YOURS(173, HttpStatus.FORBIDDEN, Sentences.MEMBER_NOT_YOURS),

    /** A member a guardian looks after, gone before their access was read. */
    MANAGED_MEMBER_NOT_HERE(174, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A guardian reaching for the access of somebody who is neither a member nor a trial member. */
    MANAGED_MEMBER_TYPE_NOT_MANAGED(
            175, HttpStatus.FORBIDDEN, "Only members and trial members are looked after this way"),

    /** The member a nickname is written for, gone before it was written. */
    NICKNAME_MEMBER_NOT_HERE(176, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A nickname written by somebody who is neither the member, nor looks after them, nor keeps the members. */
    NICKNAME_NOT_YOURS_TO_SET(
            177,
            HttpStatus.FORBIDDEN,
            "Only the member, whoever looks after them, or whoever keeps the station's members may set the "
                    + "nickname, so nothing was saved"),

    /** A nickname longer than one is kept. */
    NICKNAME_TOO_LONG(178, HttpStatus.BAD_REQUEST, "A nickname is at most 60 characters, so nothing was saved"),

    /** A nickname with a line break in it. */
    NICKNAME_NOT_ONE_LINE(179, HttpStatus.BAD_REQUEST, "A nickname is one line, so nothing was saved"),

    /** A username shorter or longer than one is kept. */
    USERNAME_LENGTH_NOT_TAKEN(
            180, HttpStatus.BAD_REQUEST, "A username is between 3 and 32 characters long, so nothing was saved"),

    /** A username with a character in it that a username does not hold, an at sign among them. */
    USERNAME_CHARACTERS_NOT_TAKEN(
            181,
            HttpStatus.BAD_REQUEST,
            "A username holds only letters, digits, dots, hyphens and underscores, so nothing was saved"),

    /** A username another account already signs in with. */
    USERNAME_TAKEN(182, HttpStatus.CONFLICT, "That username already belongs to another account, so nothing was saved"),

    /** A username taken away from an account that has nothing else to sign in with. */
    USERNAME_IS_THE_ONLY_WAY_IN(
            183,
            HttpStatus.BAD_REQUEST,
            "This account signs in with its username alone. Give it an email address before taking the "
                    + "username away"),

    /** An account writing its own address without confirming it, which is the way an account is taken over. */
    OWN_ADDRESS_NOT_WRITTEN_UNCONFIRMED(
            184, HttpStatus.FORBIDDEN, "Your own address is changed by confirming the new one, so nothing was saved"),

    /** The account an address is written onto, gone before it was written. */
    ACCOUNT_NOT_HERE_ON_ADDRESS_WRITE(185, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** An address written onto an account that is not one, or one nothing can be delivered to. */
    ADDRESS_NOT_GOOD_ON_ADDRESS_WRITE(
            186, HttpStatus.BAD_REQUEST, "That is not an address anything can be sent to, so nothing was saved"),

    /** An address written onto an account that another account already carries. */
    ADDRESS_TAKEN_ON_ADDRESS_WRITE(187, HttpStatus.CONFLICT, Sentences.ADDRESS_BELONGS_TO_ANOTHER),

    /** A second date of birth asked for at a station that already asks for one, named after it. */
    PROFILE_BIRTH_DATE_ALREADY_ASKED(
            188,
            HttpStatus.BAD_REQUEST,
            "This station already asks for the date of birth in another question, so nothing was saved"),

    /** A profile question saved with a default its own answers would not take, naming what is wrong. */
    PROFILE_DEFAULT_NOT_ACCEPTED(189, HttpStatus.BAD_REQUEST, Sentences.DEFAULT_NOT_SUITING),

    /** A member's answers read by somebody who is not them, not their guardian and may not read members. */
    PROFILE_NOT_YOURS_TO_READ(
            190, HttpStatus.FORBIDDEN, "You may only read your own answers and those of the members you look after"),

    /** A member's answers written by somebody who is not them, not their guardian and may not edit members. */
    PROFILE_NOT_YOURS_TO_WRITE(
            191,
            HttpStatus.FORBIDDEN,
            "You may only change your own answers and those of the members you look after, so nothing was saved"),

    /** An answer saved to a station question that is not one of the member's station. */
    PROFILE_FIELD_NOT_HERE_ON_ANSWER(192, HttpStatus.NOT_FOUND, Sentences.PROFILE_FIELD_NOT_HERE),

    /** An answer saved to an association question that is not asked at the member's station. */
    PROFILE_ASSOCIATION_FIELD_NOT_HERE_ON_ANSWER(
            193,
            HttpStatus.NOT_FOUND,
            "That association question is not asked at this member's station, so nothing was saved"),

    /** A group deleted while appointments, news, forms, quizzes or wiki entries are limited to it, naming how many. */
    GROUP_STILL_LIMITS_CONTENT_ON_DELETE(
            194,
            HttpStatus.CONFLICT,
            "Content is still limited to this group, so it was not removed. "
                    + "Change who may see it first. Items limited to the group"),

    /** A group turned into a tag while appointments, news, forms, quizzes or wiki entries are limited to it, naming how many. */
    GROUP_STILL_LIMITS_CONTENT_ON_CONVERT(
            195,
            HttpStatus.CONFLICT,
            "Content is still limited to this group, so it was not turned into a tag. "
                    + "Change who may see it first. Items limited to the group");

    private final Definition definition;

    MemberRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.MEMBERS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
