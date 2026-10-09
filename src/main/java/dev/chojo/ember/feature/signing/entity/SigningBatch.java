/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Fields one person signs with a single proof: one or more {@link SigningRequest}s, across documents and
 * across the members in their care, all confirmed by the step-up of the same account.
 *
 * <p>The order is the order the signer chose and saw them in; the challenge covers them in this order, so
 * it is part of what was signed. A batch of one is an ordinary single act and is bound the way one always
 * was; only a batch of two or more is bound as a batch.
 *
 * @param uid      names the batch in the evidence of every act it holds
 * @param requests the fields, in the order they are signed
 */
public record SigningBatch(UUID uid, List<SigningRequest> requests) {
    /** Copies the requests, and refuses an empty batch, two accounts, or one field named twice. */
    public SigningBatch {
        requests = List.copyOf(requests);
        if (requests.isEmpty()) throw new IllegalArgumentException("A batch holds at least one field");
        int accountId = requests.getFirst().signer().accountId();
        var fields = new HashSet<String>();
        for (SigningRequest request : requests) {
            if (request.signer().accountId() != accountId) {
                throw new IllegalArgumentException("Every field of a batch is confirmed by the same account");
            }
            if (!fields.add(request.requestUid() + "/" + request.fieldName())) {
                throw new IllegalArgumentException("The field " + request.fieldName() + " is in the batch twice");
            }
        }
    }

    /**
     * @param request the one field
     * @return a batch of that field alone
     */
    public static SigningBatch single(SigningRequest request) {
        return new SigningBatch(UUID.randomUUID(), List.of(request));
    }

    /** @return the account whose step-up confirms every field of the batch */
    public int accountId() {
        return requests.getFirst().signer().accountId();
    }

    /** @return whether the batch holds one field only, which is bound as a single act */
    public boolean single() {
        return requests.size() == 1;
    }
}
