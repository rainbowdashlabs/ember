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
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateSummary;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.media.entity.StationFile;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.media.service.MediaLibraryService.FileListing;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.owner.Owner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Jugendflamme gets the station's first document template for appointments as a document to bring
 * and the first file of its library as a file, and a station without either gets nothing.
 */
class DemoEventGroupMaterialsSeederTest {
    private static final int STATION = 3;
    private static final int GROUP = 70;

    private EventGroupService eventGroups;
    private DocumentTemplateService templates;
    private EventRequirementService requirements;
    private MediaLibraryService media;
    private EventAttachmentService attachments;
    private DemoEventGroupMaterialsSeeder seeder;
    private DemoStationContext station;

    @BeforeEach
    void wire() {
        eventGroups = mock(EventGroupService.class);
        templates = mock(DocumentTemplateService.class);
        requirements = mock(EventRequirementService.class);
        media = mock(MediaLibraryService.class);
        attachments = mock(EventAttachmentService.class);
        seeder = new DemoEventGroupMaterialsSeeder(eventGroups, templates, requirements, media, attachments);
        var group = mock(EventGroup.class);
        when(group.id()).thenReturn(GROUP);
        when(group.name()).thenReturn("Jugendflamme Stufe 1");
        when(eventGroups.findByStation(STATION)).thenReturn(List.of(group));
        var held = mock(Station.class);
        when(held.id()).thenReturn(STATION);
        station = new DemoStationContext(mock(DemoStationProfile.class), held);
    }

    private static DocumentTemplateSummary template(int id, boolean forAppointments) {
        var template = mock(DocumentTemplateSummary.class);
        when(template.id()).thenReturn(id);
        when(template.name()).thenReturn("Vorlage " + id);
        when(template.forAppointments()).thenReturn(forAppointments);
        return template;
    }

    @Test
    void theJugendflammeAsksForADocumentAndHandsOverAFile() {
        var offered = List.of(template(1, false), template(2, true));
        when(templates.list(any(), anyBoolean())).thenReturn(offered);
        var file = mock(StationFile.class);
        when(file.id()).thenReturn(40);
        when(file.fileName()).thenReturn("plan.png");
        var library = List.of(new FileListing(file, false, Set.of(), null));
        when(media.listLibrary(STATION, false)).thenReturn(library);

        seeder.seedStation(null, station);

        verify(requirements).setForEventGroup(new Owner.Station(STATION), GROUP, List.of(2));
        verify(attachments).attach(AppointmentOwner.EVENT_GROUP, GROUP, STATION, 40, "Infoblatt", false);
    }

    @Test
    void aStationWithoutTemplatesOrFilesGetsNothing() {
        var offered = List.of(template(1, false));
        when(templates.list(any(), anyBoolean())).thenReturn(offered);
        when(media.listLibrary(STATION, false)).thenReturn(List.of());

        seeder.seedStation(null, station);

        verify(requirements, never()).setForEventGroup(any(), anyInt(), anyList());
        verify(attachments, never()).attach(any(), anyInt(), anyInt(), anyInt(), anyString(), anyBoolean());
    }
}
