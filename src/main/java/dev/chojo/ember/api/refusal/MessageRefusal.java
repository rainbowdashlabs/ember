/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#MESSAGES}: messages between people.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum MessageRefusal implements Refusal {
    /** An account with a guardian whose guardians have not turned messages on for it. */
    MESSAGES_NOT_TURNED_ON(1, HttpStatus.FORBIDDEN, "Messages are not turned on for this account"),

    /**
     * A conversation that does not exist, or that the reader does not take part in. One code for
     * both, so a guessed address says nothing about whether somebody else talks there.
     */
    CONVERSATION_NOT_HERE(2, HttpStatus.NOT_FOUND, "That conversation is not here, or it is not yours to open"),

    /**
     * Somebody the reader cannot write to: unknown, sharing no station with them, without messages,
     * or blocking them. One code for every reason, because the blocked person is not to be told.
     */
    PERSON_NOT_REACHABLE(3, HttpStatus.FORBIDDEN, "That person cannot be written to"),

    /** A new group named nobody besides the reader. */
    GROUP_NEEDS_SOMEBODY(4, HttpStatus.BAD_REQUEST, "A group needs at least one other person"),

    /** A group that would hold more people than it has room for. */
    GROUP_TOO_LARGE(5, HttpStatus.BAD_REQUEST, "A group has room for 25 people, so nobody was added"),

    /** A group name longer than a name can be. */
    GROUP_NAME_TOO_LONG(6, HttpStatus.BAD_REQUEST, "A group name can be at most 100 characters long"),

    /** Renaming a conversation that is not a group. */
    RENAME_NOT_A_GROUP(7, HttpStatus.BAD_REQUEST, "Only a group has a name to change"),

    /** Renaming a group by somebody who does not own it. */
    RENAME_NOT_OWNER(8, HttpStatus.FORBIDDEN, "Only the owner of a group renames it"),

    /** Adding people to a conversation that is not a group. */
    ADD_NOT_A_GROUP(9, HttpStatus.BAD_REQUEST, "People can only be added to a group"),

    /** Removing people from a conversation that is not a group. */
    REMOVE_NOT_A_GROUP(10, HttpStatus.BAD_REQUEST, "People can only be removed from a group"),

    /** Removing somebody from a group by somebody who does not own it. */
    REMOVE_NOT_OWNER(11, HttpStatus.FORBIDDEN, "Only the owner of a group removes people from it"),

    /** The owner removing themselves, which is what leaving is for. */
    REMOVE_SELF(12, HttpStatus.BAD_REQUEST, "Leave the group instead of removing yourself"),

    /** Removing somebody who does not take part in the group. */
    REMOVE_NOT_A_MEMBER(13, HttpStatus.NOT_FOUND, "That person is not in this group"),

    /** A message with nothing in it. */
    MESSAGE_EMPTY(14, HttpStatus.BAD_REQUEST, "A message needs some text"),

    /** A message longer than a message can be. */
    MESSAGE_TOO_LONG(15, HttpStatus.BAD_REQUEST, "A message can be at most 4000 characters long"),

    /** An answer to a message that is not in the same conversation. */
    REPLY_NOT_HERE(16, HttpStatus.BAD_REQUEST, "The message being answered is not in this conversation"),

    /**
     * A message to a direct conversation whose other side blocked the writer. It does not say why,
     * because the blocked person is not to be told.
     */
    MESSAGE_NOT_DELIVERED(17, HttpStatus.FORBIDDEN, "That message could not be delivered"),

    /** A message that is not in the conversation it was asked about. */
    MESSAGE_NOT_HERE(18, HttpStatus.NOT_FOUND, "That message is not here any more"),

    /** Changing a message somebody else wrote. */
    EDIT_NOT_YOURS(19, HttpStatus.FORBIDDEN, "Only the person who wrote a message can change it"),

    /** Deleting a message somebody else wrote. */
    DELETE_NOT_YOURS(20, HttpStatus.FORBIDDEN, "Only the person who wrote a message can delete it"),

    /** Changing a message that was already deleted. */
    EDIT_DELETED(21, HttpStatus.CONFLICT, "That message was deleted, so it cannot be changed"),

    /** Reacting with something that is not a standard emoji. */
    EMOJI_NOT_TAKEN(22, HttpStatus.BAD_REQUEST, "Only a standard emoji can be reacted with"),

    /** Reacting to a system line or a deleted message. */
    REACTION_NOT_TAKEN(23, HttpStatus.BAD_REQUEST, "That cannot be reacted to"),

    /** Blocking oneself. */
    BLOCK_SELF(24, HttpStatus.BAD_REQUEST, "Nobody can block themselves"),

    /** Blocking an account that is not here. */
    BLOCK_PERSON_NOT_HERE(25, HttpStatus.NOT_FOUND, "That person is not here"),

    /** Reading a page of messages in both directions at once. */
    PAGE_BOTH_WAYS(26, HttpStatus.BAD_REQUEST, "Ask for the messages before or after one message, not both");

    private final Definition definition;

    MessageRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.MESSAGES, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
