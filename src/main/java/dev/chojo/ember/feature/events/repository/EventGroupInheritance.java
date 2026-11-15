/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

/**
 * SQL for the date settings a part of an event group takes over from its event group.
 *
 * <p>A part without an attendance template or a category of its own follows its event group's, read
 * live, so changing the event group reaches every part that has none of its own and removing a part's
 * own setting brings the event group's back. The appointment row keeps only its own setting; every read
 * that shows or uses one goes through these expressions.
 */
public final class EventGroupInheritance {

    private EventGroupInheritance() {}

    /**
     * A setting of an appointment as it takes effect: its own, or for a part without one its event
     * group's.
     *
     * @param table  the name or alias the appointment row is read under
     * @param column a column {@code station_event} and {@code event_group} both carry
     * @return the expression, without a name of its own
     */
    public static String effective(String table, String column) {
        return """
                COALESCE(%1$s.%2$s, (SELECT g.%2$s
                                     FROM event_group_set gs
                                              JOIN event_group g ON g.id = gs.event_group_id
                                     WHERE gs.id = %1$s.event_group_set_id))""".formatted(table, column);
    }

    /**
     * The attendance template an appointment's sheets are taken from.
     *
     * @param table the name or alias the appointment row is read under
     * @return the expression, without a name of its own
     */
    public static String template(String table) {
        return effective(table, "template_id");
    }

    /**
     * The category an appointment is filed under.
     *
     * @param table the name or alias the appointment row is read under
     * @return the expression, without a name of its own
     */
    public static String category(String table) {
        return effective(table, "category_id");
    }
}
