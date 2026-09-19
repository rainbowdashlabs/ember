/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/**
 * One of an appointment's own questions as a table sees it: what it is called and what its answers
 * are.
 *
 * @param label what the appointment calls the question
 * @param type  what its answers hold
 */
public record MemberTableQuestion(String label, MemberTableCellType type) {}
