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
import dev.chojo.ember.feature.signing.entity.SigningBatch;
import dev.chojo.ember.feature.signing.entity.SigningRequest;
import dev.chojo.ember.feature.signing.entity.SigningStart;

import java.util.List;

/**
 * Something that runs a person's signing act and turns it into evidence. The rest of Ember only ever sees
 * a {@link CompletedSigning}, whoever ran the act, and the station seals the document from the evidence it
 * records, whichever provider gave it.
 *
 * <p>An act has two halves. {@link #start} opens an attempt and says how it goes on: in Ember with a
 * step-up, or at an outside provider the signer is sent to, whose callback leads to {@link #complete}.
 * The caller keeps what the start returned on the server, spends it once, and hands the nonce back with
 * the confirmation; a provider holds no state between the two halves.
 *
 * <p>An attempt covers a {@link SigningBatch}: one field, or several fields the same person confirms with
 * one proof. Each field still gets evidence of its own.
 */
public interface SignatureProvider {

    /** @return the legal level this provider's signatures reach */
    SignatureLevel level();

    /**
     * Opens a signing attempt for the fields of a batch.
     *
     * @param batch the fields to sign
     * @return how the attempt goes on
     * @throws RefusalResponse when the signer has no way to confirm with this provider
     */
    SigningStart start(SigningBatch batch);

    /**
     * Checks the signer's confirmation against the batch and, when it holds, gives the evidence of the act
     * on each of its fields. The caller records them and then seals the state of each request they touch
     * ({@link SigningStateSealer}).
     *
     * @param batch        the fields the attempt was started for
     * @param confirmation the signer's confirmation, with the nonce the start issued
     * @return the level reached with the evidence of each act, in the batch's order
     * @throws RefusalResponse when the confirmation does not belong to this batch or is not accepted
     */
    List<CompletedSigning> complete(SigningBatch batch, SignerConfirmation confirmation);

    /**
     * Opens a signing attempt for one field.
     *
     * @param request the request to sign
     * @return how the attempt goes on
     * @throws RefusalResponse when the signer has no way to confirm with this provider
     */
    default SigningStart start(SigningRequest request) {
        return start(SigningBatch.single(request));
    }

    /**
     * Checks the signer's confirmation of one field.
     *
     * @param request      the request the attempt was started for
     * @param confirmation the signer's confirmation, with the nonce the start issued
     * @return the level reached with the evidence of the act
     * @throws RefusalResponse when the confirmation does not belong to this request or is not accepted
     */
    default CompletedSigning complete(SigningRequest request, SignerConfirmation confirmation) {
        return complete(SigningBatch.single(request), confirmation).getFirst();
    }
}
