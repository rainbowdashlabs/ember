/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

/**
 * SQL for the date settings a part of an event group takes over from its event group.
 *
 * <p>A part without an attendance template, a category or a participant limit of its own follows its
 * event group's, read
 * live, so changing the event group reaches every part that has none of its own and removing a part's
 * own setting brings the event group's back. The appointment row keeps only its own setting; every read
 * that shows or uses one goes through these expressions.
 *
 * <p>A part's sign-up deadline is always the event group's close: a fixed deadline at the instant the
 * event group closes, or none where it has none. A part keeps no deadline of its own while it belongs to
 * an event group.
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
     * The participant limit of an appointment: its own, or for a part without one the places per
     * part its event group gives. The event group's own limit counts people and is not this.
     *
     * @param table the name or alias the appointment row is read under
     * @return the expression, without a name of its own
     */
    public static String registrationLimit(String table) {
        return """
                COALESCE(%1$s.registration_limit, (SELECT g.part_registration_limit
                                                   FROM event_group_set gs
                                                            JOIN event_group g ON g.id = gs.event_group_id
                                                   WHERE gs.id = %1$s.event_group_set_id))""".formatted(table);
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

    /**
     * The instant the event group of a part closes, null for an appointment outside any event group.
     *
     * @param table the name or alias the appointment row is read under
     * @return the expression, without a name of its own
     */
    public static String groupClosesAt(String table) {
        return """
                (SELECT event_group_closes_at(gs.event_group_id)
                 FROM event_group_set gs
                 WHERE gs.id = %s.event_group_set_id)""".formatted(table);
    }

    /**
     * The four deadline columns of an appointment as they take effect, named as {@code station_event}
     * names them: its own rule, or for a part the event group's close as a fixed deadline.
     *
     * @param table the name or alias the appointment row is read under
     * @return the column list
     */
    public static String deadline(String table) {
        return """
                CASE WHEN %1$s.event_group_set_id IS NULL THEN %1$s.registration_deadline
                     ELSE %2$s END AS registration_deadline,
                CASE WHEN %1$s.event_group_set_id IS NULL THEN %1$s.registration_deadline_kind
                     WHEN %2$s IS NULL THEN 'NONE'
                     ELSE 'FIXED' END AS registration_deadline_kind,
                CASE WHEN %1$s.event_group_set_id IS NULL THEN %1$s.registration_deadline_offset_minutes
                     END AS registration_deadline_offset_minutes,
                CASE WHEN %1$s.event_group_set_id IS NULL THEN %1$s.registration_deadline_anchor
                     END AS registration_deadline_anchor""".formatted(table, groupClosesAt(table));
    }
}
