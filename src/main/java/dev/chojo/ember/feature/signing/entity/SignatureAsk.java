/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The signatures a generated document asks for: before anybody is asked, what asking would ask; after,
 * the request and how each of its fields stands.
 *
 * @param request the request already made for the document, open or complete, or null where none is
 * @param fields  each field with who signs it and what they confirm, in the order the document carries them
 */
public record SignatureAsk(@Nullable SignatureRequest request, List<AskedField> fields) {

    /**
     * A field of the document.
     *
     * @param field who signs it and what they confirm
     * @param state how it stands, or null before signatures were asked for
     */
    public record AskedField(
            RequestedSignature.Draft field, @Nullable FieldState state) {}

    /**
     * @param fields the fields asking would ask for
     * @return what asking would ask
     */
    public static SignatureAsk notYet(List<RequestedSignature.Draft> fields) {
        return new SignatureAsk(
                null, fields.stream().map(field -> new AskedField(field, null)).toList());
    }

    /**
     * @param request the request made for the document
     * @param fields  its fields
     * @return the request and how its fields stand
     */
    public static SignatureAsk asked(SignatureRequest request, List<RequestedSignature> fields) {
        return new SignatureAsk(
                request,
                fields.stream()
                        .map(field -> new AskedField(
                                new RequestedSignature.Draft(
                                        field.fieldName(),
                                        field.role(),
                                        field.signerId(),
                                        field.signerName(),
                                        field.capacity(),
                                        field.statement()),
                                field.state()))
                        .toList());
    }
}
