/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

/**
 * The preparation and follow-up one person on a sheet is credited with by its rules, as the sheet
 * shows it beside their name.
 *
 * <p>The minutes are what the rules give them even where the sheet says they did not do the work,
 * so whoever switches it back on sees what they are switching on. The report counts them only for
 * somebody present who was not marked.
 *
 * @param entryId            the person's row on the sheet
 * @param memberId           the person
 * @param preparationMinutes the preparation the rules give them
 * @param followUpMinutes    the follow-up the rules give them
 * @param skipped            whether the sheet says they did not do it
 */
public record ExtraTimeCredit(
        int entryId, int memberId, int preparationMinutes, int followUpMinutes, boolean skipped) {}
