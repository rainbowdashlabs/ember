/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * What the signers of one document confirm, exactly as it is shown to them.
 *
 * @param own      the statement of somebody signing their own field: the member, also through a guardian's
 *                 account, and the issuer
 * @param guardian the statement of a guardian signing on behalf of the member, which declares that they hold
 *                 custody of them
 */
public record SigningStatements(String own, String guardian) {

    /**
     * @param role who signs
     * @return the statement that signer confirms
     */
    public String of(FieldRole role) {
        return switch (role) {
            case PARTICIPANT, ISSUER -> own;
            case GUARDIAN, ANY_GUARDIAN -> guardian;
        };
    }
}
