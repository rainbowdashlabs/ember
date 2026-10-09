/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.feature.generator.entity.RequirementSignature;
import dev.chojo.ember.feature.generator.entity.RequirementSignatureField;
import dev.chojo.ember.feature.generator.entity.RequirementSignatureState;
import dev.chojo.ember.feature.generator.service.RequirementSignatures;
import dev.chojo.ember.feature.signing.entity.AppointmentRequest;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignatureWithdrawal;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Where the signatures stand on the participants' copies of the documents an appointment asks for: per
 * document and participant the latest request on a copy for the date, each field signed, open, confirmed on
 * paper or waived. A field withdrawn with its request counts as waived, since nobody is asked for it any
 * more. A copy whose agreement a signer withdrew counts as revoked as a whole, with when it was withdrawn,
 * while its fields keep what they were. The fields the reader can sign now, for themselves or a member in
 * their care, are marked, so the screen can offer the signing screen for them, and so is whether the reader
 * may withdraw what was signed ({@link WithdrawalRights}).
 */
@Singleton
public class RequirementSignatureStates implements RequirementSignatures {
    private final SignatureRequestRepository requests;
    private final SignatureRequestService requestService;
    private final WithdrawalRights rights;

    @Inject
    public RequirementSignatureStates(
            SignatureRequestRepository requests, SignatureRequestService requestService, WithdrawalRights rights) {
        this.requests = requests;
        this.requestService = requestService;
        this.rights = rights;
    }

    @Override
    public List<RequirementSignature> of(
            StationSession reader, int eventId, LocalDate date, Collection<Integer> memberIds) {
        var latest = requests.latestForAppointment(eventId, date, memberIds);
        if (latest.isEmpty()) return List.of();
        var ids = latest.stream().map(found -> found.request().id()).toList();
        var fields = requests.fieldsOfAll(ids).stream().collect(Collectors.groupingBy(RequestedSignature::requestId));
        boolean anyOpen =
                fields.values().stream().flatMap(List::stream).anyMatch(field -> field.state() == FieldState.OPEN);
        Set<Integer> yours = anyOpen ? signableBy(reader) : Set.of();
        return latest.stream()
                .map(found -> stateOf(
                        reader, found, fields.getOrDefault(found.request().id(), List.of()), yours))
                .toList();
    }

    /**
     * Where the signatures of one request stand, read the same way as those of an appointment's copies.
     *
     * @param reader     who reads them, which decides the fields they can sign now
     * @param templateId the document the request is on, as the caller names it
     * @param memberId   the member the document is about
     * @param request    the request
     * @return the request's fields with where each stands
     */
    public RequirementSignature ofRequest(
            StationSession reader, int templateId, int memberId, SignatureRequest request) {
        var fields = requests.fieldsOf(request.id());
        boolean anyOpen = fields.stream().anyMatch(field -> field.state() == FieldState.OPEN);
        Set<Integer> yours = anyOpen ? signableBy(reader) : Set.of();
        return stateOf(reader, new AppointmentRequest(templateId, memberId, request), fields, yours);
    }

    private Set<Integer> signableBy(StationSession reader) {
        return requestService.pendingFor(reader.stationId(), reader.member().id()).stream()
                .map(pending -> pending.field().id())
                .collect(Collectors.toSet());
    }

    private RequirementSignature stateOf(
            StationSession reader, AppointmentRequest found, List<RequestedSignature> fields, Set<Integer> yours) {
        var request = found.request();
        var shown = fields.stream()
                .map(field -> new RequirementSignatureField(
                        field.id(),
                        field.fieldName(),
                        field.signerName(),
                        stateOf(field.state()),
                        field.state() == FieldState.OPEN && yours.contains(field.id())))
                .toList();
        boolean revoked = request.state() == RequestState.REVOKED;
        var overall = overall(request.state(), fields);
        var withdrawnAt = revoked
                ? requests.withdrawalOf(request.id())
                        .map(SignatureWithdrawal::withdrawnAt)
                        .orElse(null)
                : null;
        return new RequirementSignature(
                found.templateId(),
                found.memberId(),
                request.uid(),
                overall,
                shown,
                rights.mayWithdraw(reader, request, fields),
                withdrawnAt);
    }

    /**
     * Where a copy stands as a whole: revoked where a signer withdrew its agreement, else as its fields say.
     *
     * @param state  where its request stands
     * @param fields its fields
     * @return the state
     */
    static RequirementSignatureState overall(RequestState state, List<RequestedSignature> fields) {
        if (state == RequestState.REVOKED) return RequirementSignatureState.REVOKED;
        return RequirementSignature.overall(
                fields.stream().map(field -> stateOf(field.state())).toList());
    }

    private static RequirementSignatureState stateOf(FieldState state) {
        return switch (state) {
            case OPEN -> RequirementSignatureState.OPEN;
            case SIGNED -> RequirementSignatureState.SIGNED;
            case PAPER_CONFIRMED -> RequirementSignatureState.PAPER_CONFIRMED;
            case WAIVED, WITHDRAWN -> RequirementSignatureState.WAIVED;
        };
    }
}
