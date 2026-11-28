/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.StationEvent;

import java.util.Map;

/**
 * Which appointments belong on a station's public calendar, the one anybody on the internet can read.
 */
public final class PublicCalendar {
    private PublicCalendar() {}

    /**
     * Whether an appointment belongs on the station's public calendar.
     *
     * <p>An appointment that not even every member may know about never does, whatever its flag is set
     * to. The flag is a tri-state whose middle value inherits from the category, so without this an
     * appointment dropped into a public category would be published by a setting nobody made for it.
     *
     * @param event      the appointment
     * @param categories the station's categories by id
     * @return whether the public calendar shows it
     */
    public static boolean shows(StationEvent event, Map<Integer, EventCategory> categories) {
        if (event.restricted()) return false;
        Boolean isPublic = event.isPublic();
        if (isPublic != null) return isPublic;
        if (event.categoryId() == null) return false;
        var category = categories.get(event.categoryId());
        return category != null && category.isPublic();
    }
}
