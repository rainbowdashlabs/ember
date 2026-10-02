/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#CLUSTERS}: clusters.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum ClusterRefusal implements Refusal {
    /** An order for a cluster's questions, sent without saying which audience the order is for. */
    CLUSTER_FIELD_ORDER_NEEDS_AN_AUDIENCE(
            1, HttpStatus.BAD_REQUEST, "An order of questions belongs to one audience, so nothing was saved"),

    /** A cluster's questions, asked for by somebody who has not said which cluster they act for. */
    NO_CLUSTER_CHOSEN_FOR_FIELDS(2, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind its questions, gone between the session being opened and the request. */
    CLUSTER_NOT_HERE_FOR_FIELDS(3, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster question given a kind of answer that is not one of the kinds on offer. */
    CLUSTER_FIELD_TYPE_UNKNOWN(4, HttpStatus.BAD_REQUEST, "That is not a kind of question, so nothing was saved"),

    /** A cluster question aimed at an audience that is not one of the audiences on offer. */
    CLUSTER_FIELD_AUDIENCE_UNKNOWN(
            5, HttpStatus.BAD_REQUEST, "That is not an audience a question can be put to, so nothing was saved"),

    /** A way of standing the cluster wiki on the public web that is not one of the ways on offer. */
    CLUSTER_PUBLIC_WIKI_MODE_UNKNOWN(
            6, HttpStatus.BAD_REQUEST, "That is not a way of putting the wiki on the public web, so nothing was saved"),

    /** A group of stations named by something that is not a number. */
    CLUSTER_STATION_GROUP_NOT_A_NUMBER(7, HttpStatus.BAD_REQUEST, "That is not a group of stations"),

    /** A part of Ember the cluster wants to withhold that is not one this instance knows. */
    CLUSTER_MODULE_UNKNOWN(
            8, HttpStatus.BAD_REQUEST, "That is not a part of Ember that can be withheld, so nothing was saved"),

    /** The cluster's own settings, asked for by somebody who has not said which cluster. */
    NO_CLUSTER_CHOSEN_FOR_GOVERNANCE(9, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind its settings, gone between the session being opened and the request. */
    CLUSTER_NOT_HERE_FOR_GOVERNANCE(10, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A look the cluster wants to hand its stations that is not one of the looks on offer. */
    CLUSTER_THEME_FEEL_UNKNOWN(11, HttpStatus.BAD_REQUEST, Sentences.LOOK_NOT_OFFERED),

    /** A new order for the steps of a chain that names no steps at all. */
    CLUSTER_CHAIN_ORDER_NEEDS_STEPS(
            12, HttpStatus.BAD_REQUEST, "Name the steps in the order they are to be walked. Nothing was saved"),

    /** A step of a chain saved without saying who walks it, what it is about and who holds the gear after. */
    CLUSTER_STEP_DETAILS_MISSING(
            13,
            HttpStatus.BAD_REQUEST,
            "A step needs who does it, what it is done to, and who holds the gear afterwards, "
                    + "so nothing was saved"),

    /** Gear sent out of the cluster's own store without saying which station it is going to. */
    CLUSTER_DISPATCH_NEEDS_A_STATION(
            14, HttpStatus.BAD_REQUEST, "Name the station the gear is going to. Nothing was sent"),

    /** What a loss report has to carry, saved without saying what that is. */
    CLUSTER_LOSS_REPORT_NEEDS_A_REQUIREMENT(
            15, HttpStatus.BAD_REQUEST, "Say what a loss report has to carry. Nothing was saved"),

    /** The cluster's gear, asked for by somebody who has not said which cluster they act for. */
    NO_CLUSTER_CHOSEN_FOR_CLUSTER_INVENTORY(16, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind its gear, gone between the session being opened and the request. */
    CLUSTER_NOT_HERE_FOR_CLUSTER_INVENTORY(17, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A chain of steps saved without saying what it is walked for. */
    CLUSTER_CHAIN_NEEDS_A_PURPOSE(18, HttpStatus.BAD_REQUEST, "A chain needs a purpose, so nothing was saved"),

    /** A chain given a purpose that is not one of the purposes on offer. */
    CLUSTER_CHAIN_PURPOSE_UNKNOWN(
            19, HttpStatus.BAD_REQUEST, "That is not a purpose a chain can have, so nothing was saved"),

    /** The words a cluster recommends for its stations' gear, asked for without saying which cluster. */
    NO_CLUSTER_CHOSEN_FOR_INVENTORY_TAGS(20, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind its recommended words, gone between the session being opened and the request. */
    CLUSTER_NOT_HERE_FOR_INVENTORY_TAGS(21, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A document filed about one of the cluster's people that arrived without the file itself. */
    CLUSTER_MEMBER_DOCUMENT_MISSING_FILE(
            22, HttpStatus.BAD_REQUEST, "That upload arrived without a file, so nothing was filed"),

    /** A document about one of the cluster's people that is larger than a document may be. */
    CLUSTER_MEMBER_DOCUMENT_TOO_LARGE(
            23, HttpStatus.BAD_REQUEST, "That file is larger than a document may be, so nothing was filed"),

    /** A document about one of the cluster's people whose bytes could not be taken in. */
    CLUSTER_MEMBER_DOCUMENT_UNREADABLE(24, HttpStatus.BAD_REQUEST, "That file could not be read, so nothing was filed"),

    /** An answer filed against a question said to come from somewhere questions do not come from. */
    CLUSTER_FIELD_ORIGIN_UNKNOWN(
            25, HttpStatus.BAD_REQUEST, "That is not somewhere a question can come from, so nothing was saved"),

    /** Somebody taken on at one of the cluster's stations without a first and a last name. */
    CLUSTER_NEW_MEMBER_NEEDS_A_NAME(
            26, HttpStatus.BAD_REQUEST, "A new member needs a first name and a last name, so nobody was taken on"),

    /** Somebody taken on at one of the cluster's stations whose address is already in use here. */
    CLUSTER_MEMBER_ALREADY_TAKEN_ON(
            27, HttpStatus.CONFLICT, "Somebody is already here with that address, so nobody was taken on"),

    /** A station named in the address of the route that takes somebody on, written as no station can be. */
    CLUSTER_NEW_MEMBER_STATION_NOT_AN_IDENTITY(
            28, HttpStatus.BAD_REQUEST, "That does not name a station, so nobody was taken on"),

    /** Somebody at a station made into something nobody can be at a station. */
    STATION_USER_TYPE_UNKNOWN_FROM_CLUSTER(
            29, HttpStatus.BAD_REQUEST, "That is not something somebody can be at a station, so nothing was changed"),

    /** Somebody at a station allowed something that cannot be allowed at a station. */
    STATION_PERMISSION_UNKNOWN_FROM_CLUSTER(
            30,
            HttpStatus.BAD_REQUEST,
            "That is not something that can be allowed at a station, so nothing was changed"),

    /** The cluster's people, asked for by somebody who has not said which cluster they act for. */
    NO_CLUSTER_CHOSEN_FOR_MEMBER_MANAGEMENT(31, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind its people, gone between the session being opened and the request. */
    CLUSTER_NOT_HERE_FOR_MEMBER_MANAGEMENT(32, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A station used to narrow a search of the cluster's people, written as no station can be. */
    CLUSTER_MEMBER_FILTER_STATION_NOT_AN_IDENTITY(33, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_AN_IDENTITY),

    /**
     * A station asked for from a cluster that does not stand over it. A station nobody has ever
     * created and a station belonging to another cluster are one code deliberately: telling them
     * apart would let a cluster administrator map the stations standing outside their own cluster.
     */
    STATION_NOT_IN_THIS_CLUSTER(34, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** The people who act for a cluster, asked for by somebody who has not said which cluster. */
    NO_CLUSTER_CHOSEN_FOR_CLUSTER_MEMBERS(35, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind the people who act for it, gone between the session and the request. */
    CLUSTER_NOT_HERE_FOR_CLUSTER_MEMBERS(36, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** Somebody in a cluster made into something nobody can be in a cluster. */
    CLUSTER_USER_TYPE_UNKNOWN(
            37, HttpStatus.BAD_REQUEST, "That is not something somebody can be in a cluster, so nothing was saved"),

    /** Somebody in a cluster allowed something that cannot be allowed in a cluster. */
    CLUSTER_PERMISSION_UNKNOWN(
            38, HttpStatus.BAD_REQUEST, "That is not something that can be allowed in a cluster, so nothing was saved"),

    /** The cluster's notices, asked for by somebody who has not said which cluster they act for. */
    NO_CLUSTER_CHOSEN_FOR_NOTIFICATIONS(39, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster just renamed, gone before it could be read back. */
    CLUSTER_NOT_HERE_AFTER_RENAME(40, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster asked to be deleted that is not here any more. */
    CLUSTER_NOT_HERE_ON_DELETE(41, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster being handed its first person to act for it that is not here any more. */
    CLUSTER_NOT_HERE_ON_APPOINTMENT(42, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster handed its first person without saying who that person is. */
    CLUSTER_APPOINTMENT_NEEDS_AN_ACCOUNT(
            43, HttpStatus.BAD_REQUEST, "Name the account that is to act for this cluster. Nobody was appointed"),

    /** The account being appointed to act for a cluster, gone before it could be appointed. */
    ACCOUNT_NOT_HERE_ON_CLUSTER_APPOINTMENT(44, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** A cluster acted on by somebody who has not said which cluster they act for. */
    NO_CLUSTER_CHOSEN(45, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster of the session, gone between the session being opened and the request. */
    CLUSTER_NOT_HERE(46, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster named in an address, written as no cluster can be. */
    CLUSTER_NOT_AN_IDENTITY(47, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_NOT_AN_IDENTITY),

    /** The way a cluster files its stations, asked for without saying which cluster. */
    NO_CLUSTER_CHOSEN_FOR_STATION_GROUPS(48, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind its groups of stations, gone between the session and the request. */
    CLUSTER_NOT_HERE_FOR_STATION_GROUPS(49, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A station a cluster is letting go that is not here any more. */
    STATION_NOT_HERE_ON_CLUSTER_RELEASE(50, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** The cluster's stations, asked for by somebody who has not said which cluster they act for. */
    NO_CLUSTER_CHOSEN_FOR_CLUSTER_STATIONS(51, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind its stations, gone between the session being opened and the request. */
    CLUSTER_NOT_HERE_FOR_CLUSTER_STATIONS(52, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A station a cluster is letting go, named in a way no station can be named. */
    STATION_NOT_AN_IDENTITY_ON_CLUSTER_RELEASE(53, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_AN_IDENTITY),

    /** The reach of a cluster's storage, saved without saying how far it reaches. */
    CLUSTER_STORAGE_POLICY_NEEDS_A_REACH(
            54, HttpStatus.BAD_REQUEST, "Say how far the cluster's storage reaches, so nothing was saved"),

    /** Storage the cluster has not set up, asked whether it answers. */
    CLUSTER_KEEPS_NO_STORAGE(
            55, HttpStatus.BAD_REQUEST, "This cluster keeps no storage of its own, so there was nothing to try"),

    /** A station whose files are being carried to the cluster's storage that is not here any more. */
    STATION_NOT_HERE_ON_CLUSTER_STORAGE_MOVE(56, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Files that could not be carried to the storage the cluster chose, so they stayed where they were. */
    CLUSTER_STORAGE_MOVE_FAILED(
            57,
            HttpStatus.BAD_REQUEST,
            "That station's files could not be carried over, so they were left where they are"),

    /** A station whose files are being carried over, named in a way no station can be named. */
    STATION_NOT_AN_IDENTITY_ON_CLUSTER_STORAGE_MOVE(58, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_AN_IDENTITY),

    /** The storage a cluster stands on, asked for by somebody who has not said which cluster. */
    NO_CLUSTER_CHOSEN_FOR_STORAGE_BACKEND(59, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind the storage it stands on, gone between the session and the request. */
    CLUSTER_NOT_HERE_FOR_STORAGE_BACKEND(60, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** Files carried between stores by a session that carries no account to record the move against. */
    NO_ACCOUNT_IN_SESSION_FOR_STORAGE_MOVE(
            61, HttpStatus.FORBIDDEN, "Sign in again before doing this, so nothing was changed"),

    /** A cluster being granted the room it may hand out that is not here any more. */
    CLUSTER_NOT_HERE_ON_POOL(62, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** The room a cluster hands out, asked for by somebody who has not said which cluster. */
    NO_CLUSTER_CHOSEN_FOR_STORAGE(63, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind the room it hands out, gone between the session and the request. */
    CLUSTER_NOT_HERE_FOR_STORAGE(64, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A station or a cluster named in a storage address, written as neither can be. */
    NOT_AN_IDENTITY_IN_CLUSTER_STORAGE(65, HttpStatus.BAD_REQUEST, "That does not name a station or a cluster"),

    /** A cluster a station is asking to join that is not here any more. */
    CLUSTER_NOT_HERE_ON_APPLICATION(66, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster a station is asking to join, named in a way no cluster can be named. */
    CLUSTER_NOT_AN_IDENTITY_ON_APPLICATION(69, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_NOT_AN_IDENTITY),

    /**
     * The settings of an association's expiry date that count days backwards or repeat without a gap,
     * the same refusal a station's own field gets.
     */
    CLUSTER_EXPIRY_SETTINGS_OUT_OF_RANGE(70, HttpStatus.BAD_REQUEST, Sentences.EXPIRY_OUT_OF_RANGE),

    /** An association question saved with a default its own answers would not take. */
    CLUSTER_PROFILE_DEFAULT_NOT_ACCEPTED(71, HttpStatus.BAD_REQUEST, Sentences.DEFAULT_NOT_SUITING),

    /** A station asking to join a cluster, asked by somebody other than its owner. */
    CLUSTER_APPLICATION_NOT_BY_STATION_OWNER(
            72, HttpStatus.FORBIDDEN, "Only the station's owner can ask to join a cluster, so nothing was sent"),

    /** A cluster's own station asking to join a cluster. */
    CLUSTER_APPLICATION_FROM_CLUSTER_HOME(
            73, HttpStatus.BAD_REQUEST, "A cluster's own station cannot join another cluster, so nothing was sent"),

    /** A station asking to join a cluster while it already belongs to one. */
    CLUSTER_APPLICATION_STATION_ALREADY_JOINED(
            74, HttpStatus.BAD_REQUEST, "This station already belongs to a cluster, so nothing was sent"),

    /** A station asking to join a cluster while a request of its own is still waiting. */
    CLUSTER_APPLICATION_ALREADY_WAITING(
            75, HttpStatus.BAD_REQUEST, "This station already has a request to join waiting, so nothing was sent"),

    /** A request to join taken back by somebody other than the station's owner. */
    CLUSTER_APPLICATION_WITHDRAWN_NOT_BY_STATION_OWNER(
            76, HttpStatus.FORBIDDEN, "Only the station's owner can take its request back, so nothing was changed"),

    /** A request to join approved for a station that joined some cluster after asking. */
    CLUSTER_APPLICATION_STATION_JOINED_MEANWHILE(
            77, HttpStatus.BAD_REQUEST, "This station has joined a cluster in the meantime, so nothing was changed"),

    /** A request to join withdrawn, approved or denied after it was already decided. */
    CLUSTER_APPLICATION_ALREADY_DECIDED(
            78, HttpStatus.BAD_REQUEST, "That request to join has already been decided, so nothing was changed"),

    /**
     * A request to join that is not here, or one addressed to another cluster.
     *
     * <p>One code for both on purpose: telling them apart would say that a station asked to join a
     * cluster the reader does not act for.
     */
    CLUSTER_APPLICATION_NOT_HERE(79, HttpStatus.NOT_FOUND, "That request to join is not here any more"),

    /** The station behind a request to join, gone while the request was being handled. */
    CLUSTER_APPLICATION_STATION_NOT_HERE(80, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** The cluster a request to join is about, gone while it was being handled. */
    CLUSTER_APPLICATION_CLUSTER_NOT_HERE(81, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A folder in the cluster's knowledge base written down without a name. */
    CLUSTER_KB_FOLDER_NEEDS_A_NAME(82, HttpStatus.BAD_REQUEST, "A folder needs a name, so nothing was saved"),

    /** An article in the cluster's knowledge base written down without a name. */
    CLUSTER_KB_ARTICLE_NEEDS_A_NAME(83, HttpStatus.BAD_REQUEST, Sentences.KB_ARTICLE_NEEDS_A_NAME),

    /**
     * A cluster article removed that is not here, or one kept at another station.
     *
     * <p>One code for both on purpose: telling them apart would say that the article exists somewhere
     * the cluster has no business with.
     */
    CLUSTER_KB_ARTICLE_NOT_HERE(84, HttpStatus.NOT_FOUND, Sentences.KB_ARTICLE_NOT_HERE),

    /** A cluster that is not here, asked for its knowledge base. */
    CLUSTER_KB_CLUSTER_NOT_HERE(85, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster that is not here, asked for the gear in its store. */
    CLUSTER_DISPATCH_CLUSTER_NOT_HERE(86, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** Gear sent out by a cluster that has no chain of its own for sending gear out. */
    CLUSTER_DISPATCH_WITHOUT_CHAIN(
            87,
            HttpStatus.BAD_REQUEST,
            "This cluster has no chain for sending gear out yet, so nothing was sent. Add one under its inventory "
                    + "settings"),

    /** Gear sent out with no piece picked. */
    CLUSTER_DISPATCH_WITHOUT_GEAR(
            88, HttpStatus.BAD_REQUEST, "Pick at least one piece of gear to send, so nothing was sent"),

    /** Gear sent out to a station that is not here. */
    CLUSTER_DISPATCH_STATION_NOT_HERE(89, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Gear sent out to a station that does not belong to the sending cluster. */
    CLUSTER_DISPATCH_STATION_NOT_IN_CLUSTER(
            90, HttpStatus.BAD_REQUEST, "That station does not belong to this cluster, so nothing was sent"),

    /** Gear sent out that is not resting in the cluster's own store. */
    CLUSTER_DISPATCH_GEAR_NOT_IN_STORE(
            91, HttpStatus.BAD_REQUEST, "Some of that gear is not in the cluster's store, so nothing was sent"),

    /** The station a cluster keeps its own things on, gone while the cluster still names it. */
    CLUSTER_GOVERNANCE_HOME_STATION_NOT_HERE(
            92, HttpStatus.NOT_FOUND, "The cluster's own station is not here any more"),

    /** Modules switched off or read through a group of stations that is gone or belongs to another cluster. */
    CLUSTER_GOVERNANCE_STATION_GROUP_NOT_OWN(
            93, HttpStatus.BAD_REQUEST, "That group of stations is not one of this cluster's"),

    /** A cluster that is not here, asked about what it allows its stations. */
    CLUSTER_GOVERNANCE_CLUSTER_NOT_HERE(94, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A second chain added for a kind of movement the cluster already has a chain for. */
    CLUSTER_INVENTORY_FLOW_PURPOSE_TAKEN(
            95,
            HttpStatus.BAD_REQUEST,
            "Nothing was saved, as that kind of movement already has a chain, which has to be archived first"),

    /**
     * A cluster chain that is not here, or one belonging to another cluster or a station.
     *
     * <p>One code for both on purpose: telling them apart would say that the chain exists somewhere
     * the cluster has no business with.
     */
    CLUSTER_INVENTORY_FLOW_NOT_HERE(96, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A step of a cluster chain that is not here. */
    CLUSTER_INVENTORY_FLOW_STEP_NOT_HERE(97, HttpStatus.NOT_FOUND, Sentences.FLOW_STEP_NOT_HERE),

    /** A cluster that is not here, asked about its gear. */
    CLUSTER_INVENTORY_CLUSTER_NOT_HERE(98, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A word recommended to stations that the cluster already recommends to them. */
    CLUSTER_INVENTORY_TAG_TAKEN_ON_CREATE(99, HttpStatus.CONFLICT, Sentences.CLUSTER_TAG_TAKEN),

    /** A recommended word changed into one the cluster already recommends to the same stations. */
    CLUSTER_INVENTORY_TAG_TAKEN_ON_CHANGE(100, HttpStatus.CONFLICT, Sentences.CLUSTER_TAG_TAKEN),

    /** A recommended word that went between being changed and being read back. */
    CLUSTER_INVENTORY_TAG_NOT_HERE_AFTER_CHANGE(
            101, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A word recommended to stations without any word in it. */
    CLUSTER_INVENTORY_TAG_NEEDS_A_NAME(102, HttpStatus.BAD_REQUEST, "A tag needs a name, so nothing was saved"),

    /**
     * A recommended word that is not here, or one another cluster recommends.
     *
     * <p>One code for both on purpose: telling them apart would say what another cluster recommends.
     */
    CLUSTER_INVENTORY_TAG_NOT_HERE(103, HttpStatus.NOT_FOUND, Sentences.CLUSTER_TAG_NOT_HERE),

    /**
     * A member taken on at a station that is not here or does not belong to the cluster.
     *
     * <p>One code for both on purpose: telling them apart would say that a station exists outside the
     * cluster.
     */
    CLUSTER_MANAGED_MEMBER_STATION_NOT_HERE(104, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Documents read or filed by a cluster at a station that keeps no documents. */
    CLUSTER_MANAGED_STATION_KEEPS_NO_DOCUMENTS(105, HttpStatus.BAD_REQUEST, "This station keeps no documents"),

    /**
     * A document a cluster asked for that is not here, or one about nobody at its stations.
     *
     * <p>One code for both on purpose: telling them apart would say what is filed at a station outside
     * the cluster.
     */
    CLUSTER_MANAGED_DOCUMENT_NOT_HERE(106, HttpStatus.NOT_FOUND, Sentences.DOCUMENT_NOT_HERE),

    /** A document a cluster may read whose file is gone from storage. */
    CLUSTER_MANAGED_DOCUMENT_FILE_NOT_HERE(107, HttpStatus.NOT_FOUND, "The file of that document is not here any more"),

    /**
     * A member a cluster reached for who is not here, whose station is gone, or who belongs to a
     * station outside the cluster.
     *
     * <p>One code for all three on purpose: telling them apart would say that somebody is a member of a
     * station outside the cluster.
     */
    CLUSTER_MANAGED_MEMBER_NOT_HERE(108, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A cluster manager changing their own membership at a station from the cluster. */
    CLUSTER_MANAGED_MEMBER_IS_YOURSELF(
            109,
            HttpStatus.FORBIDDEN,
            "Your own membership cannot be changed from the cluster, so nothing was changed"),

    /** A cluster manager changing a station's owner from the cluster. */
    CLUSTER_MANAGED_MEMBER_OWNS_STATION(
            110,
            HttpStatus.FORBIDDEN,
            "A station's owner can only be changed at their own station, so nothing was changed"),

    /** A person added to a cluster that is not here. */
    CLUSTER_MEMBER_ADDED_TO_NO_CLUSTER(111, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A person added to a cluster without an address. */
    CLUSTER_MEMBER_ADDRESS_MISSING(
            112, HttpStatus.BAD_REQUEST, "Give the email address of the person to add, so nothing was saved"),

    /** A group of cluster members being made without a name. */
    CLUSTER_MEMBER_GROUP_NAME_MISSING_ON_CREATE(113, HttpStatus.BAD_REQUEST, Sentences.GROUP_NAME_MISSING),

    /** A group of cluster members being renamed to nothing. */
    CLUSTER_MEMBER_GROUP_NAME_MISSING_ON_CHANGE(114, HttpStatus.BAD_REQUEST, Sentences.GROUP_NAME_MISSING),

    /** A permission given to a group of cluster members that this instance does not keep. */
    CLUSTER_MEMBER_GROUP_PERMISSION_UNKNOWN(
            115, HttpStatus.INTERNAL_SERVER_ERROR, "That is not a permission a group can carry, so nothing was saved"),

    /** A cluster that is not here, asked about its members or groups. */
    CLUSTER_MEMBER_CLUSTER_NOT_HERE(116, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /**
     * A cluster member that is not here, or one of another cluster.
     *
     * <p>One code for both on purpose: telling them apart would say who belongs to another cluster.
     */
    CLUSTER_MEMBER_NOT_HERE(117, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /**
     * A group of cluster members that is not here, or one of another cluster.
     *
     * <p>One code for both on purpose: telling them apart would say which groups another cluster keeps.
     */
    CLUSTER_MEMBER_GROUP_NOT_HERE(118, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE),

    /** A cluster being created with no name. */
    CLUSTER_NEEDS_A_NAME_ON_CREATE(119, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_NEEDS_A_NAME),

    /** A cluster being renamed to no name at all. */
    CLUSTER_NEEDS_A_NAME_ON_RENAME(120, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_NEEDS_A_NAME),

    /** A cluster that went before it could be renamed. */
    CLUSTER_GONE_BEFORE_RENAME(121, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster that went before it could be deleted. */
    CLUSTER_GONE_BEFORE_DELETE(122, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster asked to be deleted while stations still belong to it. */
    CLUSTER_STILL_HAS_STATIONS(
            123,
            HttpStatus.BAD_REQUEST,
            "Stations still belong to this cluster. Release them first, so nothing was deleted"),

    /** A station the cluster creates for itself, given no name. */
    CLUSTER_NEW_STATION_NEEDS_A_NAME(124, HttpStatus.BAD_REQUEST, Sentences.STATION_NEEDS_A_NAME),

    /** A cluster's own station put forward to join another cluster. */
    CLUSTER_HOME_STATION_CANNOT_JOIN(
            125, HttpStatus.BAD_REQUEST, "A cluster's own station cannot join another cluster, so nothing was changed"),

    /** A station taken into a cluster while it still belongs to another one. */
    CLUSTER_STATION_ALREADY_IN_ANOTHER(
            126, HttpStatus.BAD_REQUEST, "That station already belongs to another cluster, so nothing was changed"),

    /** A station a cluster lets go that never belonged to it. */
    CLUSTER_RELEASE_STATION_NOT_IN_IT(127, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_IN_CLUSTER),

    /** An account taken on by a cluster it already acts for. */
    CLUSTER_ACCOUNT_ALREADY_A_MEMBER(
            128, HttpStatus.CONFLICT, "That account is already a member of this cluster, so nothing was changed"),

    /** A permission granted to a cluster member that the instance keeps no record of. */
    CLUSTER_PERMISSION_NOT_KNOWN_ON_GRANT(
            129, HttpStatus.INTERNAL_SERVER_ERROR, "That permission is not known here, so nothing was granted"),

    /** The cluster a station is being created in, taken in by or released from, gone before the change. */
    CLUSTER_GONE_FOR_STATION_CHANGE(130, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** The station a cluster is taking in or letting go, gone before the change. */
    CLUSTER_STATION_GONE_FOR_CHANGE(131, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A group of stations removed while questions are still asked of it. */
    CLUSTER_STATION_GROUP_STILL_ASKED_QUESTIONS(
            132,
            HttpStatus.BAD_REQUEST,
            "Questions are still asked of this group. Point them somewhere else first, so nothing was removed"),

    /** A group of stations removed while modules are still switched off for it. */
    CLUSTER_STATION_GROUP_STILL_HAS_MODULES_OFF(
            133,
            HttpStatus.BAD_REQUEST,
            "Modules are still switched off for this group. Switch them back on first, so nothing was removed"),

    /** A group of stations removed while words are still recommended to it. */
    CLUSTER_STATION_GROUP_STILL_RECOMMENDED_TAGS(
            134,
            HttpStatus.BAD_REQUEST,
            "Tags are still recommended to this group. Point them somewhere else first, so nothing was removed"),

    /** A group of stations removed while stock requirements still count at it. */
    CLUSTER_STATION_GROUP_STILL_COUNTS_REQUIREMENTS(
            135,
            HttpStatus.BAD_REQUEST,
            "Stock requirements still count at this group. Point them somewhere else first, so nothing was removed"),

    /** A station filed under a group that is not here at all. */
    CLUSTER_STATION_GROUP_STATION_GONE(136, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A station filed under a group of a cluster it does not belong to. */
    CLUSTER_STATION_GROUP_STATION_NOT_IN_CLUSTER(137, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_IN_CLUSTER),

    /** A cluster's own station filed under one of its groups. */
    CLUSTER_STATION_GROUP_TAKES_NO_HOME_STATION(
            138, HttpStatus.BAD_REQUEST, "The cluster's own station is not one of its stations, so nothing was saved"),

    /** The cluster behind its groups of stations, gone before the change. */
    CLUSTER_STATION_GROUP_CLUSTER_GONE(139, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /**
     * A group of stations that is not here, or one of another cluster.
     *
     * <p>One code for both on purpose: telling them apart would say that the group exists at a
     * cluster the reader does not act for.
     */
    CLUSTER_STATION_GROUP_NOT_HERE(140, HttpStatus.NOT_FOUND, "That group of stations is not here any more"),

    /** A group of stations given no name. */
    CLUSTER_STATION_GROUP_NEEDS_A_NAME(
            141, HttpStatus.BAD_REQUEST, "A group of stations needs a name, so nothing was saved"),

    /** A group of stations given a name another group of the same cluster already has. */
    CLUSTER_STATION_GROUP_NAME_TAKEN(
            142,
            HttpStatus.CONFLICT,
            "This cluster already has a group of stations by that name, so nothing was saved"),

    /** A tier of room handed out without naming a station to hand it to. */
    CLUSTER_QUOTA_TIER_NAMES_NO_STATION(
            143, HttpStatus.BAD_REQUEST, "Choose at least one station to hand the tier to, so nothing was changed"),

    /** A tier handed to stations that would together take more room than the cluster has left. */
    CLUSTER_QUOTA_TIER_MORE_THAN_POOL(144, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_POOL_TOO_SMALL),

    /** Room granted to a station that would take more than the cluster has left. */
    CLUSTER_QUOTA_GRANT_MORE_THAN_POOL(145, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_POOL_TOO_SMALL),

    /** The cluster behind the room it hands out, gone before the change. */
    CLUSTER_QUOTA_CLUSTER_GONE(146, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /**
     * A tier of room that is not here, or one of another cluster.
     *
     * <p>One code for both on purpose: telling them apart would say that the tier exists at a
     * cluster the reader does not act for.
     */
    CLUSTER_QUOTA_TIER_NOT_HERE(147, HttpStatus.NOT_FOUND, "That tier is not here any more"),

    /** A station named by its identity for room from the cluster, which no station here has. */
    CLUSTER_QUOTA_STATION_NOT_KNOWN(148, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A station given room by the cluster that is not here any more. */
    CLUSTER_QUOTA_STATION_GONE(149, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Room handed to a station that does not belong to the cluster. */
    CLUSTER_QUOTA_STATION_NOT_IN_CLUSTER(150, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_IN_CLUSTER),

    /** A tier of room given no name. */
    CLUSTER_QUOTA_TIER_NEEDS_A_NAME(151, HttpStatus.BAD_REQUEST, "A tier needs a name, so nothing was saved"),

    /** A tier of room given a name another tier of the same cluster already has. */
    CLUSTER_QUOTA_TIER_NAME_TAKEN(
            152, HttpStatus.BAD_REQUEST, "This cluster already has a tier by that name, so nothing was saved"),

    /** Room set to less than nothing, which is a typing mistake. */
    CLUSTER_QUOTA_ROOM_BELOW_NOTHING(
            153, HttpStatus.BAD_REQUEST, "Room cannot be less than nothing, so nothing was saved"),

    /** A reach chosen for the cluster's storage before any storage has been set up. */
    CLUSTER_STORAGE_REACH_WITHOUT_STORAGE(
            154,
            HttpStatus.BAD_REQUEST,
            "Set up the cluster's storage before deciding what it is for, so nothing was saved"),

    /** A station's files moved that are already where the cluster's storage says they belong. */
    CLUSTER_STORAGE_STATION_ALREADY_IN_PLACE(
            155, HttpStatus.BAD_REQUEST, "That station's files are already where they belong, so nothing was moved"),

    /** A station's files moved onto the cluster's storage when the cluster keeps none. */
    CLUSTER_STORAGE_NONE_TO_MOVE_ONTO(
            156, HttpStatus.BAD_REQUEST, "This cluster keeps no storage to move the files onto, so nothing was moved"),

    /** A station's files moved by the cluster when they stand on storage the station brought itself. */
    CLUSTER_STORAGE_STATION_OWN_STORAGE(
            157,
            HttpStatus.BAD_REQUEST,
            "That station keeps its files on storage of its own, which only the station can change. Nothing was moved"),

    /** The cluster whose storage is being set or used, gone before the change. */
    CLUSTER_STORAGE_CLUSTER_GONE(158, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A station whose files the cluster moves, gone before the move. */
    CLUSTER_STORAGE_STATION_GONE(159, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A station whose files the cluster moves that does not belong to the cluster. */
    CLUSTER_STORAGE_STATION_NOT_IN_CLUSTER(160, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_IN_CLUSTER),

    /** A cluster question pointed at a group of stations of another cluster. */
    CLUSTER_PROFILE_FIELD_GROUP_NOT_OWN(
            162, HttpStatus.BAD_REQUEST, "That group of stations belongs to another cluster, so nothing was saved"),

    /** A cluster question named like another one that reaches some of the same stations. */
    CLUSTER_PROFILE_FIELD_NAME_REACHES_TWICE(
            163,
            HttpStatus.BAD_REQUEST,
            "A question of that name already reaches a station this one would reach, so nothing was saved"),

    /** A cluster question given no name. */
    CLUSTER_PROFILE_FIELD_NEEDS_A_NAME(164, HttpStatus.BAD_REQUEST, "A question needs a name, so nothing was saved"),

    /** A cluster question of a type a cluster does not ask, which is the date of birth a station asks itself. */
    CLUSTER_PROFILE_FIELD_TYPE_NOT_OFFERED(
            165,
            HttpStatus.BAD_REQUEST,
            "A station asks for the date of birth itself, so a cluster cannot ask for it as well. Nothing was saved"),

    /** The cluster behind its questions, gone before the request was carried out. */
    CLUSTER_PROFILE_FIELD_CLUSTER_GONE(166, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /**
     * A cluster question that is not here, or one of another cluster.
     *
     * <p>One code for both on purpose: telling them apart would say that the question exists at a
     * cluster the reader does not act for.
     */
    CLUSTER_PROFILE_FIELD_NOT_HERE(167, HttpStatus.NOT_FOUND, Sentences.PROFILE_FIELD_NOT_HERE),

    /** A member archived from the association who their station could not archive either. */
    CLUSTER_MANAGED_MEMBER_NOT_ARCHIVED(170, HttpStatus.BAD_REQUEST, Sentences.MEMBER_NOT_YET_FORMER),

    /** An association question put to, or taken from, an audience the request does not name. */
    CLUSTER_FIELD_AUDIENCE_MISSING(
            171, HttpStatus.BAD_REQUEST, "A question is put to one kind of member, so nothing was saved"),

    /** An association question put to trial members, whom an association does not ask. */
    CLUSTER_FIELD_AUDIENCE_NOT_ASKED(
            172, HttpStatus.BAD_REQUEST, "An association does not ask trial members, so nothing was saved");

    private final Definition definition;

    ClusterRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.CLUSTERS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
