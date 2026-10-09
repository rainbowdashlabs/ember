/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.api.MemberIdentity;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A member of a partner station registered for an appointment on a date, with where each document the
 * appointment asks partners to sign stands for them, as the station holding the appointment sees it.
 *
 * @param registrationId the registration the partner sent
 * @param member         the member as the partner shares them: the name it shares, else a member of that
 *                       partner; null once the partnership is gone
 * @param documents      every document the appointment asks partners to sign, in its order
 */
public record PartnerSigner(
        int registrationId, @Nullable MemberIdentity member, List<PartnerSignerDocument> documents) {

    public PartnerSigner {
        documents = List.copyOf(documents);
    }
}
