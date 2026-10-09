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
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Where the signatures stand on the participants' copies of the documents an appointment asks for: per
 * document and participant the latest request on a copy for the date, each field signed, open, confirmed on
 * paper or waived. A field withdrawn with its request counts as waived, since nobody is asked for it any
 * more. The fields the reader can sign now, for themselves or a member in their care, are marked, so the
 * screen can offer the signing screen for them.
 */
@Singleton
public class RequirementSignatureStates implements RequirementSignatures {
    private final SignatureRequestRepository requests;
    private final SignatureRequestService requestService;

    @Inject
    public RequirementSignatureStates(SignatureRequestRepository requests, SignatureRequestService requestService) {
        this.requests = requests;
        this.requestService = requestService;
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
        return latest.stream().map(found -> stateOf(found, fields, yours)).toList();
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
        return stateOf(new AppointmentRequest(templateId, memberId, request), Map.of(request.id(), fields), yours);
    }

    private Set<Integer> signableBy(StationSession reader) {
        return requestService.pendingFor(reader.stationId(), reader.member().id()).stream()
                .map(pending -> pending.field().id())
                .collect(Collectors.toSet());
    }

    private static RequirementSignature stateOf(
            AppointmentRequest found, Map<Integer, List<RequestedSignature>> fields, Set<Integer> yours) {
        var shown = fields.getOrDefault(found.request().id(), List.of()).stream()
                .map(field -> new RequirementSignatureField(
                        field.id(),
                        field.fieldName(),
                        field.signerName(),
                        stateOf(field.state()),
                        field.state() == FieldState.OPEN && yours.contains(field.id())))
                .toList();
        var overall = RequirementSignature.overall(
                shown.stream().map(RequirementSignatureField::state).toList());
        return new RequirementSignature(
                found.templateId(), found.memberId(), found.request().uid(), overall, shown);
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
