/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Who an event template says its appointments are for.
 *
 * <p>The audience a template carries is not a restriction on the template: nobody attends a
 * template. It is what every appointment written from it starts with, so that a station running one
 * evening for the youngest group does not pick that group again on every date of the year. Applying
 * the template copies it onto the appointment, and from there it is the appointment's own and can be
 * changed without touching the template.
 */
@Singleton
public class EventTemplateRestrictionService {
    private static final Logger log = LoggerFactory.getLogger(EventTemplateRestrictionService.class);

    private final EventTemplateRepository templateRepository;
    private final RestrictionService restrictionService;

    @Inject
    public EventTemplateRestrictionService(
            EventTemplateRepository templateRepository, RestrictionService restrictionService) {
        this.templateRepository = templateRepository;
        this.restrictionService = restrictionService;
    }

    /**
     * The audience a template hands on.
     *
     * @param templateId the template
     * @return the audience, empty where the template names nobody in particular
     */
    public RestrictionSet findRestrictions(int templateId) {
        var template = templateRepository.findById(templateId).orElse(null);
        RestrictionMode mode = template != null && template.restrictionMode() != null
                ? template.restrictionMode()
                : RestrictionMode.AND;
        return restrictionService.findRestrictionSet(RestrictionType.EVENT_TEMPLATE, templateId, mode);
    }

    /**
     * Replaces the audience a template hands on.
     *
     * @param templateId the template
     * @param selection  the audience to keep
     */
    public void setRestrictions(int templateId, RestrictionSelection selection) {
        restrictionService.setRestrictions(RestrictionType.EVENT_TEMPLATE, templateId, selection);
        log.info("Set restrictions for event template {}", templateId);
    }

    /**
     * Sets how the named audiences of a template combine.
     *
     * @param templateId the template
     * @param mode       whether every kind named has to match or any one of them
     */
    public void updateRestrictionMode(int templateId, RestrictionMode mode) {
        templateRepository.updateRestrictionMode(templateId, mode);
        log.info("Updated restriction mode for event template {} to {}", templateId, mode);
    }
}
