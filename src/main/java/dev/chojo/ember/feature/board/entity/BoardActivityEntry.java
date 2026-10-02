/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import java.time.Instant;

/**
 * One entry of a ticket's activity feed: a comment, a move between lanes or a change to the ticket.
 *
 * @param type      which kind of entry the id points at
 * @param id        the id of the comment, transition or history entry
 * @param timestamp when it happened
 */
public record BoardActivityEntry(BoardActivityType type, int id, Instant timestamp) {}
