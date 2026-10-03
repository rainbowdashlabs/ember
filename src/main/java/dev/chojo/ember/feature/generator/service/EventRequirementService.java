/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
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
 */
@Singleton
public class EventRequirementService {
    private static final Logger log = LoggerFactory.getLogger(EventRequirementService.class);

    /** The most documents one appointment asks for. */
    static final int MAX_REQUIREMENTS = 10;

    private final EventRequirementRepository requirements;
    private final DocumentTemplateService templates;

    @Inject
    public EventRequirementService(EventRequirementRepository requirements, DocumentTemplateService templates) {
        this.requirements = requirements;
        this.templates = templates;
    }

    /**
     * What appointments and appointment templates of the station may ask for.
     *
     * @param owner the station
     * @return the templates for appointments in use, by name
     */
    public List<RequiredTemplate> offered(Owner.Station owner) {
        return requirements.offered(owner.stationId());
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
        var checked = checked(owner, templateIds, requirements.forEvent(eventId));
        requirements.replaceForEvent(eventId, checked);
        log.info("Appointment {} asks for {} document(s)", eventId, checked.size());
        return requirements.forEvent(eventId);
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
