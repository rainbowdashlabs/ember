/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.generator.service.DocumentGeneratorService.Prepared;
import dev.chojo.ember.feature.generator.service.DocumentGeneratorService.Rendered;

import java.util.function.IntConsumer;

/**
 * Signs a drawn letter for its issuer before it is filed, where the issuer agreed to letters being signed
 * without them signing each by hand. The generator only hands the letter over and files what comes back;
 * whether and how it is signed is the signing feature's decision.
 *
 * <p>Signing may wait for outside services, so it runs before the letter is filed and outside any
 * transaction around the filing. What it signed is recorded once the letter has its entry in the
 * generation log, in the filing's transaction.
 */
public interface IssuerSigning {

    /** Signs nothing: every letter is filed as it was drawn. */
    IssuerSigning NONE = (prepared, rendered) -> Signed.unsigned(rendered);

    /**
     * @param prepared what the letter was drawn from, which names its issuer
     * @param rendered the letter as drawn
     * @return the letter to file, signed or as it was, with what to record once it is filed
     */
    Signed sign(Prepared prepared, Rendered rendered);

    /**
     * A letter ready to be filed.
     *
     * @param rendered the letter, its file signed for the issuer or as it was drawn
     * @param record   records the signing against the letter's generation log entry, given its id; does
     *                 nothing for a letter that was not signed
     */
    record Signed(Rendered rendered, IntConsumer record) {

        /**
         * @param rendered the letter as drawn
         * @return the letter filed as it was, with nothing to record
         */
        public static Signed unsigned(Rendered rendered) {
            return new Signed(rendered, generationId -> {});
        }
    }
}
