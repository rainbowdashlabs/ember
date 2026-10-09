/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;

/**
 * Who may withdraw a signed agreement.
 *
 * <p>An agreement is withdrawn as a whole, by the member it is about, by a guardian acting for them, or by
 * whoever signed one of its fields for themselves or the member, for as long as it stands: open or complete,
 * with at least one field signed online or confirmed on paper. Its issuer's signature is no consent, so
 * signing as issuer gives no right to withdraw, and an agreement nobody signed yet has nothing to withdraw.
 */
@Singleton
public class WithdrawalRights {
    private final GuardianPolicy guardians;

    @Inject
    public WithdrawalRights(GuardianPolicy guardians) {
        this.guardians = guardians;
    }

    /**
     * @param reader  who would withdraw
     * @param request the request
     * @param fields  its fields
     * @return whether the reader may withdraw the agreement now
     */
    public boolean mayWithdraw(StationSession reader, SignatureRequest request, List<RequestedSignature> fields) {
        return standing(request) && agreed(fields) && isParty(reader, request, fields);
    }

    /**
     * Refuses a withdrawal the reader may not make.
     *
     * @param reader  who withdraws
     * @param request the request, as held
     * @param fields  its fields, as held
     */
    void requireMayWithdraw(StationSession reader, SignatureRequest request, List<RequestedSignature> fields) {
        if (!isParty(reader, request, fields)) throw DocumentRefusal.SIGNATURE_WITHDRAWAL_NOT_YOURS.raise();
        if (!standing(request)) throw DocumentRefusal.SIGNATURE_WITHDRAWAL_ENDED.raise();
        if (!agreed(fields)) throw DocumentRefusal.SIGNATURE_WITHDRAWAL_NOTHING_SIGNED.raise();
    }

    /**
     * @param reader  who withdraws
     * @param request the request
     * @return {@link SignerCapacity#GUARDIAN} where the reader acts for the member as their guardian, else
     *     {@link SignerCapacity#ACCOUNT_HOLDER}
     */
    SignerCapacity capacityOf(StationSession reader, SignatureRequest request) {
        Integer memberId = request.memberId();
        boolean guardian =
                memberId != null && memberId != reader.member().id() && guardians.mayActFor(reader.user(), memberId);
        return guardian ? SignerCapacity.GUARDIAN : SignerCapacity.ACCOUNT_HOLDER;
    }

    /**
     * @param reader  the reader
     * @param request the request
     * @param fields  its fields
     * @return whether the reader is a party to the agreement: they act for its member or signed a field of it
     */
    public boolean isParty(StationSession reader, SignatureRequest request, List<RequestedSignature> fields) {
        Integer memberId = request.memberId();
        if (memberId != null && guardians.mayActFor(reader.user(), memberId)) return true;
        int me = reader.member().id();
        return fields.stream()
                .anyMatch(field -> field.state() == FieldState.SIGNED
                        && field.role() != FieldRole.ISSUER
                        && Objects.equals(field.settledBy(), me));
    }

    private static boolean standing(SignatureRequest request) {
        return request.state() == RequestState.OPEN || request.state() == RequestState.COMPLETE;
    }

    private static boolean agreed(List<RequestedSignature> fields) {
        return fields.stream()
                .anyMatch(field -> (field.state() == FieldState.SIGNED || field.state() == FieldState.PAPER_CONFIRMED)
                        && field.role() != FieldRole.ISSUER);
    }
}
