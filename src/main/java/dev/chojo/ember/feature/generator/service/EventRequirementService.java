/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventRequirementsChanged;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.generator.entity.RequiredTemplate;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * The documents an appointment or an appointment template asks participants to bring.
 *
 * <p>Only a template of the station marked for appointments can be asked for, since only such a
 * template is meant to be filled with an appointment's values and handed to participants. A template
 * archived since it was asked for stays on the list until somebody takes it off, but none can be added.
 * An appointment made from an appointment template starts with the template's list
 * ({@code EventTemplateService.copyInto}).
 *
 * <p>An appointment shared with partner stations asks their members to sign the same document as every
 * other signer: a document to bring that asks the member side to sign has to be about nobody in particular
 * there ({@link MemberNeutralTemplates}). That is asked when the documents of a shared appointment are set,
 * and when an appointment that asks for documents is shared ({@link #requireShareable}).
 *
 * <p>Changing what an appointment asks for is told to the rest of the station ({@link EventRequirementsChanged}),
 * so the participants already registered are asked for a document added later, and what is still open on one
 * taken off goes. Changing an appointment template only affects the appointments made from it afterwards, so
 * nobody is told.
 */
@Singleton
public class EventRequirementService {
    private static final Logger log = LoggerFactory.getLogger(EventRequirementService.class);

    /** The most documents one appointment asks for. */
    static final int MAX_REQUIREMENTS = 10;

    private final EventRequirementRepository requirements;
    private final DocumentTemplateService templates;
    private final MemberNeutralTemplates neutral;
    private final EventFederationRepository shares;
    private final DomainEventBus eventBus;

    @Inject
    public EventRequirementService(
            EventRequirementRepository requirements,
            DocumentTemplateService templates,
            MemberNeutralTemplates neutral,
            EventFederationRepository shares,
            DomainEventBus eventBus) {
        this.requirements = requirements;
        this.templates = templates;
        this.neutral = neutral;
        this.shares = shares;
        this.eventBus = eventBus;
    }

    /**
     * Refuses to share an appointment one of whose documents to bring partners could not sign alike.
     *
     * @param eventId the appointment, already checked to be the station's
     */
    public void requireShareable(int eventId) {
        requirements.forEvent(eventId).stream()
                .filter(required -> !required.archived())
                .flatMap(required -> templates.find(required.templateId()).stream())
                .forEach(neutral::requireSignableByPartners);
    }

    /**
     * What appointments and appointment templates of the station may ask for.
     *
     * @param owner the station
     * @param query the search, order and page
     * @return the page asked for of the station's own templates for appointments in use
     */
    public TemplateQuery.TemplatePage offered(Owner.Station owner, TemplateQuery query) {
        return query.pageOf(templates.list(owner, false).stream()
                .filter(template -> template.forAppointments() && !template.ofAssociation())
                .toList());
    }

    /**
     * @param eventId the appointment, already checked to be the station's
     * @return what it asks participants to bring, in its order
     */
    public List<RequiredTemplate> forEvent(int eventId) {
        return requirements.forEvent(eventId);
    }

    /**
     * @param eventTemplateId the appointment template, already checked to be the station's
     * @return what it hands to the appointments made from it, in its order
     */
    public List<RequiredTemplate> forEventTemplate(int eventTemplateId) {
        return requirements.forEventTemplate(eventTemplateId);
    }

    /**
     * Sets what an appointment asks participants to bring.
     *
     * @param owner       the station
     * @param eventId     the appointment, already checked to be the station's
     * @param templateIds the templates in their order
     * @return what it asks for now
     */
    public List<RequiredTemplate> setForEvent(Owner.Station owner, int eventId, List<Integer> templateIds) {
        var before = requirements.forEvent(eventId);
        var checked = checked(owner, templateIds, before);
        if (shares.findShareByEvent(eventId).isPresent()) {
            checked.forEach(templateId ->
                    neutral.requireSignableByPartners(templates.requireUsable(owner.stationId(), templateId)));
        }
        requirements.replaceForEvent(eventId, checked);
        log.info("Appointment {} asks for {} document(s)", eventId, checked.size());
        announceChange(owner, eventId, before, checked);
        return requirements.forEvent(eventId);
    }

    /** Tells the station which documents the appointment asks for now that it did not, and the other way round. */
    private void announceChange(Owner.Station owner, int eventId, List<RequiredTemplate> before, List<Integer> now) {
        var previous = before.stream().map(RequiredTemplate::templateId).toList();
        var added = now.stream().filter(id -> !previous.contains(id)).toList();
        var removed = previous.stream().filter(id -> !now.contains(id)).toList();
        if (added.isEmpty() && removed.isEmpty()) return;
        eventBus.publish(new EventRequirementsChanged(owner.stationId(), eventId, added, removed));
    }

    /**
     * Sets what an appointment template hands to the appointments made from it.
     *
     * @param owner           the station
     * @param eventTemplateId the appointment template, already checked to be the station's
     * @param templateIds     the templates in their order
     * @return what it hands over now
     */
    public List<RequiredTemplate> setForEventTemplate(
            Owner.Station owner, int eventTemplateId, List<Integer> templateIds) {
        var checked = checked(owner, templateIds, requirements.forEventTemplate(eventTemplateId));
        requirements.replaceForEventTemplate(eventTemplateId, checked);
        log.info("Appointment template {} hands on {} document(s)", eventTemplateId, checked.size());
        return requirements.forEventTemplate(eventTemplateId);
    }

    /**
     * The templates asked for, each once, refusing one that is not the station's, one not meant for
     * appointments, one archived that was not asked for before, and too many.
     */
    private List<Integer> checked(Owner.Station owner, List<Integer> templateIds, List<RequiredTemplate> before) {
        var chosen = templateIds.stream().filter(Objects::nonNull).distinct().toList();
        if (chosen.size() > MAX_REQUIREMENTS) {
            throw DocumentRefusal.DOCUMENT_REQUIREMENTS_TOO_MANY.raise(RefusalDetail.count(MAX_REQUIREMENTS));
        }
        var kept = before.stream().map(RequiredTemplate::templateId).toList();
        for (int templateId : chosen) {
            var template = kept.contains(templateId)
                    ? templates.requireUsable(owner.stationId(), templateId)
                    : templates.requireInUse(owner.stationId(), templateId);
            if (!template.forAppointments()) {
                throw DocumentRefusal.DOCUMENT_TEMPLATE_NOT_FOR_APPOINTMENTS.raise(RefusalDetail.text(template.name()));
            }
        }
        return chosen;
    }
}
