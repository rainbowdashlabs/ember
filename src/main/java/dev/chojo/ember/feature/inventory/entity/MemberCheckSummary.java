/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import dev.chojo.ember.api.auth.StationUserType;

import java.time.Instant;

/**
 * Summary of a member's inventory check status, including lock information and roles.
 *
 * @param memberId         the member ID
 * @param firstName        the member's first name
 * @param lastName         the member's last name
 * @param lastCheckedAt    when the member was last checked, or {@code null} if never
 * @param checkerFirstName the first name of the person who last checked
 * @param checkerLastName  the last name of the person who last checked
 * @param locked           whether the member is currently locked for checking
 * @param lockedBy         the member who holds the lock, or {@code null}
 * @param lockerFirstName  the locker's first name
 * @param lockerLastName   the locker's last name
 * @param userType         the member's user type
 */
public record MemberCheckSummary(
        int memberId,
        String firstName,
        String lastName,
        Instant lastCheckedAt,
        String checkerFirstName,
        String checkerLastName,
        boolean locked,
        Integer lockedBy,
        String lockerFirstName,
        String lockerLastName,
        StationUserType userType) {}
