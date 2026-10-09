/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.SignatureSummary;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Map;

/**
 * How the signatures on documents stand, for the lists that show documents: the member documents, the
 * station's store and the generated documents. Each list reads the state of a whole page at once.
 *
 * <p>Whoever may see a document in a list may see how its signatures stand; who is asked and what they
 * confirmed is the request's own view and needs the right to manage the document.
 */
@Singleton
public class SignatureSummaries {
    private final SignatureRequestRepository requests;

    @Inject
    public SignatureSummaries(SignatureRequestRepository requests) {
        this.requests = requests;
    }

    /**
     * @param documentIds member documents
     * @return how the signatures on each stand, by document id; none for a document nobody was asked to sign
     */
    public Map<Integer, SignatureSummary> ofDocuments(Collection<Integer> documentIds) {
        return requests.summariesOfDocuments(documentIds);
    }

    /**
     * @param generationIds generation log entries
     * @return how the signatures on each generated document stand, by generation id; none for a document
     *     nobody was asked to sign
     */
    public Map<Integer, SignatureSummary> ofGenerations(Collection<Integer> generationIds) {
        return requests.summariesOfGenerations(generationIds);
    }
}
