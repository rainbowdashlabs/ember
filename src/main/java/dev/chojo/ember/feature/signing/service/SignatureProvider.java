/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.signing.entity.CompletedSigning;
import dev.chojo.ember.feature.signing.entity.SignatureLevel;
import dev.chojo.ember.feature.signing.entity.SignerConfirmation;
import dev.chojo.ember.feature.signing.entity.SigningRequest;
import dev.chojo.ember.feature.signing.entity.SigningStart;

/**
 * Something that runs a person's signing act and turns it into evidence. The rest of Ember only ever sees
 * a {@link CompletedSigning}, whoever ran the act, and the station seals the document from the evidence it
 * records, whichever provider gave it.
 *
 * <p>An act has two halves. {@link #start} opens an attempt and says how it goes on: in Ember with a
 * step-up, or at an outside provider the signer is sent to, whose callback leads to {@link #complete}.
 * The caller keeps what the start returned on the server, spends it once, and hands the nonce back with
 * the confirmation; a provider holds no state between the two halves.
 */
public interface SignatureProvider {

    /** @return the legal level this provider's signatures reach */
    SignatureLevel level();

    /**
     * Opens a signing attempt for the request.
     *
     * @param request the request to sign
     * @return how the attempt goes on
     * @throws RefusalResponse when the signer has no way to confirm with this provider
     */
    SigningStart start(SigningRequest request);

    /**
     * Checks the signer's confirmation against the request and, when it holds, gives the evidence of the
     * act. The caller records it and then seals the request's state ({@link SigningStateSealer}).
     *
     * @param request      the request the attempt was started for
     * @param confirmation the signer's confirmation, with the nonce the start issued
     * @return the level reached with the evidence of the act
     * @throws RefusalResponse when the confirmation does not belong to this request or is not accepted
     */
    CompletedSigning complete(SigningRequest request, SignerConfirmation confirmation);
}
