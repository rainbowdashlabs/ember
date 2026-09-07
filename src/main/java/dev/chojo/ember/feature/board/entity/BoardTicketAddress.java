/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

/**
 * Where a ticket's page is, which is at its board and its number rather than at its id.
 *
 * <p>Every other thing a comment hangs under is reached by a plain id, so an event naming one by id
 * says enough to open it. A ticket does not, and the two travel together or not at all, which is
 * what this pair says.
 *
 * @param boardKey     the short key of the board holding the ticket
 * @param ticketNumber the ticket's number within that board
 */
public record BoardTicketAddress(String boardKey, int ticketNumber) {}
