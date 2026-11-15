/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.events.entity.AppointmentOwner;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.service.EventAttachmentService;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Hands the Jugendflamme a document to bring and a file, which every one of its dates then shows before
 * its own.
 *
 * <p>Both come from what other seeders leave at the station, the document templates for appointments
 * and the media library, so this runs in a band after them. A station without either simply gets none.
 */
@Singleton
public class DemoEventGroupMaterialsSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoEventGroupMaterialsSeeder.class);

    private final EventGroupService eventGroups;
    private final DocumentTemplateService templates;
    private final EventRequirementService requirements;
    private final MediaLibraryService media;
    private final EventAttachmentService attachments;

    @Inject
    public DemoEventGroupMaterialsSeeder(
            EventGroupService eventGroups,
            DocumentTemplateService templates,
            EventRequirementService requirements,
            MediaLibraryService media,
            EventAttachmentService attachments) {
        this.eventGroups = eventGroups;
        this.templates = templates;
        this.requirements = requirements;
        this.media = media;
        this.attachments = attachments;
    }

    @Override
    public int order() {
        return SHOWCASE;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        int stationId = station.stationId();
        var jugendflamme = eventGroups.findByStation(stationId).stream()
                .filter(group -> group.name().startsWith("Jugendflamme"))
                .map(EventGroup::id)
                .findFirst();
        if (jugendflamme.isEmpty()) return;
        int eventGroupId = jugendflamme.get();
        var owner = new Owner.Station(stationId);
        templates.list(owner, false).stream()
                .filter(template -> template.forAppointments() && !template.ofAssociation())
                .findFirst()
                .ifPresent(template -> {
                    requirements.setForEventGroup(owner, eventGroupId, List.of(template.id()));
                    log.info("Demo: The Jugendflamme at station {} asks for {}", stationId, template.name());
                });
        media.listLibrary(stationId, false).stream().findFirst().ifPresent(listing -> {
            attachments.attach(
                    AppointmentOwner.EVENT_GROUP,
                    eventGroupId,
                    stationId,
                    listing.file().id(),
                    "Infoblatt",
                    false);
            log.info(
                    "Demo: The Jugendflamme at station {} hands over {}",
                    stationId,
                    listing.file().fileName());
        });
    }
}
