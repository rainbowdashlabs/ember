/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.MemberView;
import dev.chojo.ember.feature.procedure.service.DocumentStepTemplates;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The document templates a step of a procedure may generate: those in use at the station that are not
 * meant for appointments and ask the member or a guardian to sign, since a step is settled by those
 * signatures.
 *
 * <p>Whether the member side signs is read from the document as drawn for everybody, the same test the
 * signing request applies when it is asked for, so a step never names a template that request refuses.
 */
@Singleton
public class ProcedureDocumentTemplates implements DocumentStepTemplates {
    private final DocumentTemplateService templates;
    private final DocumentGeneratorService generator;

    @Inject
    public ProcedureDocumentTemplates(DocumentTemplateService templates, DocumentGeneratorService generator) {
        this.templates = templates;
        this.generator = generator;
    }

    @Override
    public boolean suitable(int stationId, int templateId) {
        try {
            var template = templates.requireInUse(stationId, templateId);
            return !template.forAppointments()
                    && generator.sourceOf(template).asksMemberSideToSign(MemberView.EVERYBODY);
        } catch (RefusalResponse refused) {
            return false;
        }
    }

    @Override
    public boolean meantFor(int stationId, int templateId, int memberId) {
        return usableAt(stationId, templateId)
                .map(template -> templates.fitFor(stationId, memberId).meantFor(template, memberId))
                .orElse(false);
    }

    @Override
    public List<StepDocument> named(int stationId, Collection<Integer> templateIds) {
        return templateIds.stream()
                .distinct()
                .flatMap(id -> usableAt(stationId, id).stream())
                .map(template -> new StepDocument(template.id(), template.name()))
                .toList();
    }

    private Optional<DocumentTemplate> usableAt(int stationId, int templateId) {
        try {
            return Optional.of(templates.requireUsable(stationId, templateId));
        } catch (RefusalResponse refused) {
            return Optional.empty();
        }
    }
}
