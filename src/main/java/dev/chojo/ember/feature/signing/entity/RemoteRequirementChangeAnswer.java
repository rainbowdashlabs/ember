/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.util.List;

/**
 * What a partner station answers once it carried a change of a shared appointment's documents over to its
 * members.
 *
 * @param released the documents taken off whose open requests it let go, per member and date
 */
public record RemoteRequirementChangeAnswer(List<RemoteAgreementRelease> released) {

    public RemoteRequirementChangeAnswer {
        released = List.copyOf(released);
    }
}
