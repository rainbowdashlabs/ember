/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.service.AttendanceTemplateGuards;
import dev.chojo.ember.feature.events.entity.AppointmentOwner;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventTemplateSettings;
import dev.chojo.ember.feature.events.repository.EventAttachmentRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What an appointment template carries besides its settings: the starting values of its attendance
 * sheet's fields and the partners its appointments are shared with.
 */
class EventTemplateCarriedSettingsTest extends RepositoryTestBase {
    private static EventTemplateService templates;
    private static EventFieldDefaultService fieldDefaults;
    private static EventTemplateShareService shares;
    private static Station station;
    private static Station otherStation;

    @BeforeAll
    static void setup() {
        var guards = new AttendanceTemplateGuards(attendanceRepo);
        var templateRepository = new EventTemplateRepository();
        templates = new EventTemplateService(templateRepository, attendanceRepo, memberEligibility, guards);
        fieldDefaults = new EventFieldDefaultService(
                eventFieldDefaultRepo, eventRepo, templateRepository, attendanceRepo, guards);
        shares = new EventTemplateShareService(new EventFederationRepository(), new FederationRepository());
        station = stationRepo.create("TemplateCarriedStation");
        otherStation = stationRepo.create("TemplateCarriedOtherStation");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
    }

    private static int sheetWithField(String name) {
        var sheet = attendanceRepo.createTemplate(station.id(), name);
        attendanceRepo.createTemplateField(sheet.id(), "Leitung", FieldType.TEXT, AttendanceFieldConfig.parse("{}"), 0);
        return sheet.id();
    }

    private static int fieldOf(int sheetId) {
        return attendanceRepo.findTemplateFields(sheetId).getFirst().id();
    }

    private static int templateOn(@Nullable Integer sheetId) {
        int id = templates.create(station.id(), "Vorlage " + UUID.randomUUID()).id();
        if (sheetId != null) templates.update(id, onSheet("Vorlage " + id, sheetId));
        return id;
    }

    private static EventTemplateSettings onSheet(String name, @Nullable Integer sheetId) {
        return new EventTemplateSettings(
                name, null, null, null, null, null, null, null, null, sheetId, null, null, null, null);
    }

    private static EventFieldDefault value(int templateId, int fieldId, String value) {
        return new EventFieldDefault(templateId, fieldId, "VALUE", value);
    }

    @Test
    void aTemplateKeepsStartingValuesForTheFieldsOfItsSheet() {
        int sheet = sheetWithField("Bogen eins");
        int field = fieldOf(sheet);
        int template = templateOn(sheet);

        fieldDefaults.set(AppointmentOwner.TEMPLATE, template, List.of(value(template, field, "Greta")));

        var stored = fieldDefaults.find(AppointmentOwner.TEMPLATE, template);
        assertEquals(1, stored.size());
        assertEquals("Greta", stored.getFirst().value());
        assertEquals(template, stored.getFirst().eventId());
    }

    /**
     * A starting value for a field of another sheet, or of no sheet at all, would fill in a sheet
     * nobody opens, so it is left out rather than stored.
     */
    @Test
    void aStartingValueForAFieldOffTheSheetIsLeftOut() {
        int sheet = sheetWithField("Bogen zwei");
        int foreign = fieldOf(sheetWithField("Bogen fremd"));
        int template = templateOn(sheet);
        int withoutSheet = templateOn(null);

        fieldDefaults.set(AppointmentOwner.TEMPLATE, template, List.of(value(template, foreign, "Greta")));
        fieldDefaults.set(AppointmentOwner.TEMPLATE, withoutSheet, List.of(value(withoutSheet, foreign, "Greta")));

        assertTrue(fieldDefaults.find(AppointmentOwner.TEMPLATE, template).isEmpty());
        assertTrue(fieldDefaults.find(AppointmentOwner.TEMPLATE, withoutSheet).isEmpty());
    }

    @Test
    void aStartingValueForADeletedFieldIsRefused() {
        int sheet = sheetWithField("Bogen drei");
        int field = fieldOf(sheet);
        int template = templateOn(sheet);
        attendanceRepo.archiveTemplateField(sheet, field);

        assertThrows(
                RefusalResponse.class,
                () -> fieldDefaults.set(AppointmentOwner.TEMPLATE, template, List.of(value(template, field, "Greta"))));
    }

    @Test
    void deletingAFieldTakesItsStartingValueOffTheTemplate() {
        int sheet = sheetWithField("Bogen vier");
        int field = fieldOf(sheet);
        int template = templateOn(sheet);
        fieldDefaults.set(AppointmentOwner.TEMPLATE, template, List.of(value(template, field, "Greta")));

        attendanceRepo.archiveTemplateField(sheet, field);

        assertTrue(fieldDefaults.find(AppointmentOwner.TEMPLATE, template).isEmpty());
    }

    @Test
    void deletingTheSheetTakesTheStartingValuesOffTheTemplate() {
        int sheet = sheetWithField("Bogen fuenf");
        int field = fieldOf(sheet);
        int template = templateOn(sheet);
        fieldDefaults.set(AppointmentOwner.TEMPLATE, template, List.of(value(template, field, "Greta")));

        attendanceRepo.archiveTemplate(sheet);

        assertTrue(fieldDefaults.find(AppointmentOwner.TEMPLATE, template).isEmpty());
        assertNull(templates.findById(template).orElseThrow().attendanceTemplateId());
    }

    /** A template moved to another sheet drops what it held for the old one, and keeps it otherwise. */
    @Test
    void movingTheTemplateToAnotherSheetDropsTheOldStartingValues() {
        int sheet = sheetWithField("Bogen sechs");
        int field = fieldOf(sheet);
        int template = templateOn(sheet);
        fieldDefaults.set(AppointmentOwner.TEMPLATE, template, List.of(value(template, field, "Greta")));

        templates.update(template, onSheet("Umbenannt", sheet));
        assertEquals(1, fieldDefaults.find(AppointmentOwner.TEMPLATE, template).size(), "same sheet, kept");

        templates.update(template, onSheet("Umbenannt", sheetWithField("Bogen sieben")));
        assertTrue(fieldDefaults.find(AppointmentOwner.TEMPLATE, template).isEmpty());
    }

    /** A template names only partners of its own station; anything else is left out. */
    @Test
    void aTemplateSharesWithPartnersOfItsOwnStationOnly() {
        var federation = new FederationRepository();
        var ours = federation.createPartner(station.id(), UUID.randomUUID(), null, null, null);
        var theirs = federation.createPartner(otherStation.id(), UUID.randomUUID(), null, null, null);
        int template = templateOn(null);

        var share = shares.set(station.id(), template, ShareScope.SPECIFIC, List.of(ours.id(), theirs.id(), ours.id()));

        assertEquals(List.of(ours.id()), share.partnerIds());
        var found = shares.find(template).orElseThrow();
        assertEquals(ShareScope.SPECIFIC, found.share().scope());
        assertEquals(List.of(ours.id()), found.partnerIds());
    }

    @Test
    void sharingWithEveryPartnerNamesNoneAndCanBeTakenBack() {
        var ours = new FederationRepository().createPartner(station.id(), UUID.randomUUID(), null, null, null);
        int template = templateOn(null);
        shares.set(station.id(), template, ShareScope.SPECIFIC, List.of(ours.id()));

        shares.set(station.id(), template, ShareScope.ALL_PARTNERS, List.of(ours.id()));
        var found = shares.find(template).orElseThrow();
        assertEquals(ShareScope.ALL_PARTNERS, found.share().scope());
        assertTrue(found.partnerIds().isEmpty());

        shares.remove(template);
        assertTrue(shares.find(template).isEmpty());
    }

    /**
     * A station whose templates and events attach files can still be deleted: the files are kept
     * from deletion only while something attaches them, and the station takes both with it.
     */
    @Test
    void aStationWithAttachedTemplateAndEventFilesCanBeDeleted() {
        var doomed = stationRepo.create("TemplateCarriedDoomedStation");
        int file = mediaFileRepo
                .create(null, doomed.id(), UUID.randomUUID().toString(), "plan.pdf", "application/pdf", 10)
                .id();
        int template = templates.create(doomed.id(), "Vorlage").id();
        var event = eventRepo.create(
                doomed.id(),
                "Dienst",
                "",
                dev.chojo.ember.feature.events.entity.StationEvent.EventType.ONE_TIME,
                null,
                java.time.Instant.parse("2026-05-12T18:00:00Z"),
                java.time.Instant.parse("2026-05-12T20:00:00Z"),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        var attachments = new EventAttachmentRepository();
        attachments.attach(AppointmentOwner.TEMPLATE, template, file, null, false);
        attachments.attach(AppointmentOwner.EVENT, event.id(), file, null, true);

        assertTrue(stationRepo.delete(doomed.id()));
        assertTrue(stationRepo.findById(doomed.id()).isEmpty());
    }
}
