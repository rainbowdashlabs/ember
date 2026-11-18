/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.AttendanceRefusal;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.attendance.service.AttendanceTemplateGuards;
import dev.chojo.ember.feature.events.entity.AppointmentOwner;
import dev.chojo.ember.feature.events.entity.DateSettingIds;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventGroupPartSettings;
import dev.chojo.ember.feature.events.entity.EventGroupSettings;
import dev.chojo.ember.feature.events.repository.EventCategoryRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventGroupSettingsRepository;
import dev.chojo.ember.feature.events.repository.EventReminderRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * The date settings an event group hands to its parts: the attendance template with its field
 * pre-fills, the category and the reminders.
 *
 * <p>A part without a setting of its own follows the event group's, read live wherever the setting
 * matters (the attendance sheet, the reminder job, lists and the calendar), so changing the event group
 * reaches every part that has none of its own. A part's own setting replaces the event group's; there is
 * no way for a part to say "explicitly none", since removing its own setting is how it falls back.
 *
 * <p>The field pre-fills belong to the attendance template they fill in: a part with a template of its
 * own uses its own pre-fills, a part following the event group's template the event group's.
 */
@Singleton
public class EventGroupSettingsService {
    private final EventGroupService eventGroups;
    private final EventGroupRepository eventGroupRepository;
    private final EventGroupSettingsRepository settings;
    private final EventReminderRepository reminders;
    private final EventFieldDefaultService fieldDefaults;
    private final AttendanceRepository attendance;
    private final AttendanceTemplateGuards templateGuards;
    private final EventCategoryRepository categories;

    @Inject
    public EventGroupSettingsService(
            EventGroupService eventGroups,
            EventGroupRepository eventGroupRepository,
            EventGroupSettingsRepository settings,
            EventReminderRepository reminders,
            EventFieldDefaultService fieldDefaults,
            AttendanceRepository attendance,
            AttendanceTemplateGuards templateGuards,
            EventCategoryRepository categories) {
        this.eventGroups = eventGroups;
        this.eventGroupRepository = eventGroupRepository;
        this.settings = settings;
        this.reminders = reminders;
        this.fieldDefaults = fieldDefaults;
        this.attendance = attendance;
        this.templateGuards = templateGuards;
        this.categories = categories;
    }

    /**
     * What an event group hands to its parts.
     *
     * @param stationId    the station asking
     * @param eventGroupId the event group
     * @return its date settings
     */
    public EventGroupSettings find(int stationId, int eventGroupId) {
        eventGroups.requireEventGroup(stationId, eventGroupId);
        return read(eventGroupId);
    }

    /**
     * Sets what an event group hands to its parts, all of it or, where anything is refused, none.
     *
     * <p>The template and the category have to be the station's, and a template deleted since cannot be
     * chosen. Reminders are kept once each and only where they lie at least a day ahead. Pre-fills are
     * kept only for fields the template uses, and none without a template.
     *
     * @param stationId    the station asking
     * @param eventGroupId the event group
     * @param wanted       the settings, the field pre-fills' owner ids ignored
     * @return the settings as they are now
     */
    public EventGroupSettings save(int stationId, int eventGroupId, EventGroupSettings wanted) {
        Integer templateId = wanted.templateId();
        Integer categoryId = wanted.categoryId();
        requireTemplateOf(stationId, templateId);
        requireCategoryOf(stationId, categoryId);
        var days = wanted.reminderDays().stream()
                .filter(Objects::nonNull)
                .filter(day -> day > 0)
                .distinct()
                .sorted()
                .toList();
        var defaults = fieldDefaults.checkedForEventGroup(
                templateId,
                wanted.fieldDefaults().stream()
                        .map(entry ->
                                new EventFieldDefault(eventGroupId, entry.fieldId(), entry.source(), entry.value()))
                        .toList());
        return Transactions.call(() -> {
            eventGroups.lockEventGroup(stationId, eventGroupId);
            settings.setOfEventGroup(eventGroupId, templateId, categoryId);
            reminders.replaceEventGroupDays(eventGroupId, days);
            fieldDefaults.replaceOfEventGroup(eventGroupId, defaults);
            return read(eventGroupId);
        });
    }

    /**
     * What a part of an event group sets itself and what it takes over, for its editor.
     *
     * @param stationId the station asking
     * @param eventId   the appointment
     * @return the settings, empty for an appointment that is not a part of an event group of the station
     */
    public Optional<EventGroupPartSettings> ofPart(int stationId, int eventId) {
        return eventGroupRepository
                .findPart(eventId)
                .flatMap(part -> eventGroupRepository.findById(part.eventGroupId()))
                .filter(group -> group.stationId() == stationId)
                .map(group -> {
                    var own = settings.findOwnOfEvent(eventId).orElse(new DateSettingIds(null, null));
                    return new EventGroupPartSettings(
                            group.id(),
                            own.templateId(),
                            own.categoryId(),
                            read(group.id()),
                            settings.findOwnLimit(eventId),
                            group.details().partRegistrationLimit());
                });
    }

    /**
     * Gives a part of an event group places of its own, or takes them away so it takes the places per
     * part of its event group again.
     *
     * @param stationId the station asking
     * @param eventId   the part
     * @param limit     its places, at least one, or null for the event group's
     * @return what the part sets itself and takes over now
     */
    public EventGroupPartSettings setPartLimit(int stationId, int eventId, @Nullable Integer limit) {
        if (limit != null && limit < 1) throw EventRefusal.EVENT_GROUP_LIMIT_NOT_POSITIVE.raise();
        var part = eventGroupRepository.findPart(eventId).orElseThrow(EventRefusal.EVENT_GROUP_NOT_HERE::raise);
        Transactions.run(() -> {
            eventGroups.lockEventGroup(stationId, part.eventGroupId());
            settings.setOwnLimit(eventId, limit);
        });
        return ofPart(stationId, eventId).orElseThrow(EventRefusal.EVENT_GROUP_NOT_HERE::raise);
    }

    private EventGroupSettings read(int eventGroupId) {
        var ids = settings.findOfEventGroup(eventGroupId).orElse(new DateSettingIds(null, null));
        return new EventGroupSettings(
                ids.templateId(),
                ids.categoryId(),
                reminders.findEventGroupDays(eventGroupId),
                fieldDefaults.find(AppointmentOwner.EVENT_GROUP, eventGroupId));
    }

    private void requireTemplateOf(int stationId, @Nullable Integer templateId) {
        if (templateId == null) return;
        attendance
                .findTemplateById(templateId)
                .filter(template -> template.stationId() == stationId)
                .orElseThrow(AttendanceRefusal.ATTENDANCE_TEMPLATE_NOT_HERE_OR_NOT_YOURS::raise);
        templateGuards.requireOpenForNewWork(templateId);
    }

    private void requireCategoryOf(int stationId, @Nullable Integer categoryId) {
        if (categoryId == null) return;
        categories
                .findById(categoryId)
                .filter(category -> category.stationId() == stationId)
                .orElseThrow(EventRefusal.EVENT_GROUP_CATEGORY_NOT_HERE::raise);
    }
}
