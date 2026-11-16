/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.DateSettingIds;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The attendance template and category an event group hands to its parts, and the ones a part names
 * itself. The reminders and field pre-fills of an event group live with those of appointments, in
 * {@link EventReminderRepository} and {@link EventFieldDefaultRepository}.
 */
@Singleton
public class EventGroupSettingsRepository {

    /**
     * @param eventGroupId the event group
     * @return its attendance template and category, empty for no such event group
     */
    public Optional<DateSettingIds> findOfEventGroup(int eventGroupId) {
        return query("SELECT template_id, category_id FROM event_group WHERE id = :id;")
                .single(call().bind("id", eventGroupId))
                .map(DateSettingIds.map())
                .first();
    }

    /**
     * Sets the attendance template and category an event group hands to its parts.
     *
     * @param eventGroupId the event group
     * @param templateId   the attendance template, or null for none
     * @param categoryId   the category, or null for none
     */
    public void setOfEventGroup(int eventGroupId, @Nullable Integer templateId, @Nullable Integer categoryId) {
        query("UPDATE event_group SET template_id = :template_id, category_id = :category_id WHERE id = :id;")
                .single(call().bind("id", eventGroupId)
                        .bind("template_id", templateId)
                        .bind("category_id", categoryId))
                .update();
    }

    /**
     * The attendance template and category an appointment names itself, leaving out what it takes over
     * from an event group.
     *
     * @param eventId the appointment
     * @return its own ids, empty for no such appointment
     */
    public Optional<DateSettingIds> findOwnOfEvent(int eventId) {
        return query("SELECT template_id, category_id FROM station_event WHERE id = :id;")
                .single(call().bind("id", eventId))
                .map(DateSettingIds.map())
                .first();
    }
}
