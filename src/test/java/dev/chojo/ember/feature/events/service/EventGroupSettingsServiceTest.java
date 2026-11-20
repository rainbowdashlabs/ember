/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.AttendanceRefusal;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.service.AttendanceTemplateGuards;
import dev.chojo.ember.feature.events.entity.AppointmentOwner;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.EventGroupSettings;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventGroupSettingsRepository;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.repository.EventGroupFixtures.part;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An event group hands its attendance template, field pre-fills, category and reminders to every part
 * without its own: a part's own setting replaces the event group's, and removing it brings the event
 * group's back.
 */
class EventGroupSettingsServiceTest extends RepositoryTestBase {
    private static Station station;
    private static Station otherStation;

    private final EventGroupRepository groupRepo = new EventGroupRepository();
    private EventGroupService groups;
    private EventGroupSettingsService service;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Group Settings Station");
        otherStation = stationRepo.create("Other Group Settings Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
    }

    @BeforeEach
    void wire() {
        groups = newEventGroupService();
        var guards = new AttendanceTemplateGuards(attendanceRepo);
        service = new EventGroupSettingsService(
                groups,
                groupRepo,
                new EventGroupSettingsRepository(),
                eventReminderRepo,
                new EventFieldDefaultService(
                        eventFieldDefaultRepo, eventRepo, new EventTemplateRepository(), attendanceRepo, guards),
                attendanceRepo,
                guards,
                eventCategoryRepo);
    }

    private EventGroup group() {
        return groups.create(
                station.id(),
                new EventGroupDetails("Jugendflamme", null, null, null, false, null),
                List.of(new EventGroupSetDraft(null, "Übung", 0)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
    }

    private int templateWithField(Station at, String name) {
        var template = attendanceRepo.createTemplate(at.id(), name);
        attendanceRepo.createTemplateField(
                template.id(), "Ausbilder", FieldType.TEXT, AttendanceFieldConfig.parse("{}"), 0);
        return template.id();
    }

    private int fieldOf(int templateId) {
        return attendanceRepo.findTemplateFields(templateId).getFirst().id();
    }

    private static void setOwn(int eventId, String column, Integer value) {
        query("UPDATE station_event SET %s = :value WHERE id = :id;", column)
                .single(call().bind("value", value).bind("id", eventId))
                .update();
    }

    private static EventGroupSettings settings(
            Integer templateId, Integer categoryId, List<Integer> days, List<EventFieldDefault> defaults) {
        return new EventGroupSettings(templateId, categoryId, days, defaults);
    }

    @Test
    void savesWhatTheGroupHandsDown() {
        var group = group();
        int templateId = templateWithField(station, "Übungsbogen");
        var category = eventCategoryRepo.create(station.id(), "Ausbildung", 0, null);
        int field = fieldOf(templateId);

        var saved = service.save(
                station.id(),
                group.id(),
                settings(
                        templateId,
                        category.id(),
                        List.of(3, 1, 3, 0, -2),
                        List.of(
                                new EventFieldDefault(0, field, "VALUE", "Herr Krause"),
                                new EventFieldDefault(0, field + 999, "VALUE", "fremd"))));

        assertEquals(templateId, saved.templateId());
        assertEquals(category.id(), saved.categoryId());
        assertEquals(List.of(1, 3), saved.reminderDays());
        assertEquals(1, saved.fieldDefaults().size());
        assertEquals("Herr Krause", saved.fieldDefaults().getFirst().value());
        assertEquals(group.id(), saved.fieldDefaults().getFirst().eventId());
        assertEquals(saved, service.find(station.id(), group.id()));
    }

    @Test
    void preFillsNeedTheTemplate() {
        var group = group();
        int field = fieldOf(templateWithField(station, "Ohne Bogen"));

        var saved = service.save(
                station.id(),
                group.id(),
                settings(null, null, List.of(), List.of(new EventFieldDefault(0, field, "EVENT_NAME", null))));

        assertTrue(saved.fieldDefaults().isEmpty());
    }

    @Test
    void refusesWhatIsNotTheStations() {
        var group = group();
        int foreignTemplate = templateWithField(otherStation, "Fremder Bogen");
        var foreignCategory = eventCategoryRepo.create(otherStation.id(), "Fremd", 0, null);

        var template = assertThrows(
                RefusalResponse.class,
                () -> service.save(station.id(), group.id(), settings(foreignTemplate, null, List.of(), List.of())));
        assertEquals(AttendanceRefusal.ATTENDANCE_TEMPLATE_NOT_HERE_OR_NOT_YOURS, template.refusal());

        var category = assertThrows(
                RefusalResponse.class,
                () -> service.save(
                        station.id(), group.id(), settings(null, foreignCategory.id(), List.of(), List.of())));
        assertEquals(EventRefusal.EVENT_GROUP_CATEGORY_NOT_HERE, category.refusal());

        var elsewhere = assertThrows(
                RefusalResponse.class,
                () -> service.save(otherStation.id(), group.id(), settings(null, null, List.of(), List.of())));
        assertEquals(EventRefusal.EVENT_GROUP_NOT_HERE, elsewhere.refusal());
    }

    @Test
    void refusesADeletedTemplate() {
        var group = group();
        int templateId = templateWithField(station, "Gelöschter Bogen");
        attendanceRepo.archiveTemplate(templateId);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.save(station.id(), group.id(), settings(templateId, null, List.of(), List.of())));

        assertEquals(AttendanceRefusal.ATTENDANCE_TEMPLATE_ARCHIVED, refused.refusal());
    }

    @Test
    void aPartFollowsTheGroupUntilItSetsItsOwn() {
        var group = group();
        var event = part(group, 5);
        int groupTemplate = templateWithField(station, "Gruppenbogen");
        int ownTemplate = templateWithField(station, "Eigener Bogen");
        var groupCategory = eventCategoryRepo.create(station.id(), "Lehrgang", 0, null);
        var ownCategory = eventCategoryRepo.create(station.id(), "Prüfung", 1, null);
        service.save(station.id(), group.id(), settings(groupTemplate, groupCategory.id(), List.of(), List.of()));

        var following = eventRepo.findById(event.id()).orElseThrow();
        assertEquals(groupTemplate, following.templateId());
        assertEquals(groupCategory.id(), following.categoryId());

        setOwn(event.id(), "template_id", ownTemplate);
        setOwn(event.id(), "category_id", ownCategory.id());
        var own = eventRepo.findById(event.id()).orElseThrow();
        assertEquals(ownTemplate, own.templateId());
        assertEquals(ownCategory.id(), own.categoryId());

        setOwn(event.id(), "template_id", null);
        setOwn(event.id(), "category_id", null);
        var back = eventRepo.findById(event.id()).orElseThrow();
        assertEquals(groupTemplate, back.templateId());
        assertEquals(groupCategory.id(), back.categoryId());
    }

    @Test
    void aChangeToTheGroupReachesItsParts() {
        var group = group();
        var event = part(group, 6);
        var first = eventCategoryRepo.create(station.id(), "Erste", 0, null);
        var second = eventCategoryRepo.create(station.id(), "Zweite", 1, null);
        service.save(station.id(), group.id(), settings(null, first.id(), List.of(), List.of()));
        service.save(station.id(), group.id(), settings(null, second.id(), List.of(), List.of()));

        assertEquals(second.id(), eventRepo.findById(event.id()).orElseThrow().categoryId());
        var filtered = eventRepo.findFiltered(station.id(), null, second.id(), null);
        assertTrue(filtered.stream().anyMatch(found -> found.id() == event.id()));
    }

    @Test
    void remindersFallBackToTheGroup() {
        var group = group();
        var event = part(group, 7);
        service.save(station.id(), group.id(), settings(null, null, List.of(2, 5), List.of()));

        assertEquals(List.of(2, 5), eventReminderRepo.findEffectiveDays(event.id()));
        assertTrue(eventRepo.findEventsWithReminders().stream().anyMatch(found -> found.id() == event.id()));
        assertTrue(eventReminderRepo.findDays(event.id()).isEmpty());

        eventReminderRepo.replace(event.id(), List.of(1));
        assertEquals(List.of(1), eventReminderRepo.findEffectiveDays(event.id()));

        eventReminderRepo.replace(event.id(), List.of());
        assertEquals(List.of(2, 5), eventReminderRepo.findEffectiveDays(event.id()));
    }

    @Test
    void preFillsFollowTheTemplateThePartUses() {
        var group = group();
        var event = part(group, 8);
        int groupTemplate = templateWithField(station, "Bogen der Reihe");
        int groupField = fieldOf(groupTemplate);
        service.save(
                station.id(),
                group.id(),
                settings(
                        groupTemplate,
                        null,
                        List.of(),
                        List.of(new EventFieldDefault(0, groupField, "VALUE", "Reihe"))));

        var inherited = eventFieldDefaultRepo.findEffective(event.id());
        assertEquals(1, inherited.size());
        assertEquals("Reihe", inherited.getFirst().value());
        assertEquals(event.id(), inherited.getFirst().eventId());

        int ownTemplate = templateWithField(station, "Bogen des Tages");
        int ownField = fieldOf(ownTemplate);
        setOwn(event.id(), "template_id", ownTemplate);
        eventFieldDefaultRepo.replace(
                AppointmentOwner.EVENT,
                event.id(),
                List.of(new EventFieldDefault(event.id(), ownField, "VALUE", "Tag")));

        var own = eventFieldDefaultRepo.findEffective(event.id());
        assertEquals(1, own.size());
        assertEquals("Tag", own.getFirst().value());
    }

    @Test
    void deletingTheTemplateLetsTheGroupGoOfIt() {
        var group = group();
        int templateId = templateWithField(station, "Bald gelöscht");
        service.save(
                station.id(),
                group.id(),
                settings(
                        templateId,
                        null,
                        List.of(),
                        List.of(new EventFieldDefault(0, fieldOf(templateId), "VALUE", "x"))));

        attendanceRepo.archiveTemplate(templateId);

        var after = service.find(station.id(), group.id());
        assertNull(after.templateId());
        assertTrue(after.fieldDefaults().isEmpty());
    }

    @Test
    void aPartSeesItsOwnAndTheGroupsSettings() {
        var group = group();
        var event = part(group, 9);
        var category = eventCategoryRepo.create(station.id(), "Eigen", 0, null);
        setOwn(event.id(), "category_id", category.id());
        service.save(station.id(), group.id(), settings(null, null, List.of(4), List.of()));

        var ofPart = service.ofPart(station.id(), event.id()).orElseThrow();
        assertEquals(group.id(), ofPart.eventGroupId());
        assertEquals(category.id(), ofPart.ownCategoryId());
        assertNull(ofPart.ownTemplateId());
        assertEquals(List.of(4), ofPart.eventGroup().reminderDays());

        assertTrue(service.ofPart(otherStation.id(), event.id()).isEmpty());
    }

    @Test
    void anAppointmentOfItsOwnHasNoGroupSettings() {
        var start = Instant.now().plus(Duration.ofDays(3));
        var event = eventRepo.create(
                station.id(),
                "Allein",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(Duration.ofHours(1)),
                null,
                false,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);

        assertTrue(service.ofPart(station.id(), event.id()).isEmpty());
    }

    @Test
    void aPartTakesThePlacesPerPartUntilItGetsItsOwnAndKeepsThemWhenTheGroupIsSaved() {
        var group = group();
        var set = groupRepo.findSets(group.id()).getFirst();
        int partId = part(group, set.id(), 12).id();
        groups.update(
                station.id(),
                group.id(),
                new EventGroupDetails("Jugendflamme", null, null, null, false, null, null, null, 10, null));

        var inherited = service.ofPart(station.id(), partId).orElseThrow();
        assertNull(inherited.ownRegistrationLimit());
        assertEquals(10, inherited.partRegistrationLimit());
        assertEquals(10, eventRepo.findById(partId).orElseThrow().registrationLimit());

        var own = service.setPartLimit(station.id(), partId, 4);
        assertEquals(4, own.ownRegistrationLimit());
        groups.update(
                station.id(),
                group.id(),
                new EventGroupDetails("Jugendflamme", null, null, null, false, null, null, null, 12, null));
        assertEquals(4, eventRepo.findById(partId).orElseThrow().registrationLimit(), "saving the group keeps it");

        service.setPartLimit(station.id(), partId, null);
        assertEquals(12, eventRepo.findById(partId).orElseThrow().registrationLimit());
    }

    @Test
    void aPartGetsAtLeastOnePlaceAndOnlyAtItsOwnStation() {
        var group = group();
        int partId =
                part(group, groupRepo.findSets(group.id()).getFirst().id(), 13).id();

        var none = assertThrows(RefusalResponse.class, () -> service.setPartLimit(station.id(), partId, 0));
        assertEquals(EventRefusal.EVENT_GROUP_LIMIT_NOT_POSITIVE, none.refusal());
        var foreign = assertThrows(RefusalResponse.class, () -> service.setPartLimit(otherStation.id(), partId, 3));
        assertEquals(EventRefusal.EVENT_GROUP_NOT_HERE, foreign.refusal());
        var notAPart =
                assertThrows(RefusalResponse.class, () -> service.setPartLimit(station.id(), Integer.MAX_VALUE, 3));
        assertEquals(EventRefusal.EVENT_GROUP_NOT_HERE, notAPart.refusal());
    }
}
