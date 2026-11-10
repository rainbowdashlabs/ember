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
        return usableAt(stationId, templateId)
                .filter(template -> !template.archived())
                .filter(ProcedureDocumentTemplates::mayBeAStep)
                .filter(template -> generator.sourceOf(template).asksMemberSideToSign(MemberView.EVERYBODY))
                .isPresent();
    }

    /**
     * Whether a template may be the document of a step at all: one meant for appointments is not, since it
     * is drawn for an appointment a procedure does not have. Choosing a step's document and generating it
     * both ask this.
     *
     * @param template the template
     * @return whether a step may name it
     */
    static boolean mayBeAStep(DocumentTemplate template) {
        return !template.forAppointments();
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
