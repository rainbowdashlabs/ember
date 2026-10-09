/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A member of a partner station holding a place at a shared appointment on a date, as the organiser names
 * them to the partner.
 *
 * @param memberUid the member, by the id their registration names
 * @param eventDate the date
 */
public record RemoteRegisteredMember(UUID memberUid, LocalDate eventDate) {}
