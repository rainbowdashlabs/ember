/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.api.auth.StationUserType;

/**
 * A person a witness may pick: one to collect a signature from, or one who signs in front of them.
 *
 * @param memberId the member
 * @param name     their official name, which the document and the signature carry
 * @param userType what kind of member they are, which tells a trial user apart
 */
public record WitnessPerson(int memberId, String name, StationUserType userType) {}
