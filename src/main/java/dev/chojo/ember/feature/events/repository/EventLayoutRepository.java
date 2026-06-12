/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventLayout;
import dev.chojo.ember.feature.events.entity.EventLayoutField;
import dev.chojo.ember.feature.events.entity.LayoutFieldEntry;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class EventLayoutRepository {

    public List<EventLayout> findByStation(int stationId) {
        return query("SELECT id, station_id, name FROM event_layout WHERE station_id = :station_id ORDER BY name;")
                .single(call().bind("station_id", stationId))
                .map(EventLayout.map())
                .all();
    }

    public Optional<EventLayout> findById(int id) {
        return query("SELECT id, station_id, name FROM event_layout WHERE id = :id;")
                .single(call().bind("id", id))
                .map(EventLayout.map())
                .first();
    }

    public EventLayout create(int stationId, String name) {
        return query(
                        "INSERT INTO event_layout(station_id, name) VALUES (:station_id, :name) RETURNING id, station_id, name;")
                .single(call().bind("station_id", stationId).bind("name", name))
                .map(EventLayout.map())
                .first()
                .orElseThrow();
    }

    public boolean update(int id, String name) {
        return query("UPDATE event_layout SET name = :name WHERE id = :id;")
                .single(call().bind("id", id).bind("name", name))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return query("DELETE FROM event_layout WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    public List<EventLayoutField> findFieldsByLayout(int layoutId) {
        return query("""
                SELECT
                    id,
                    layout_id,
                    name,
                    field_type,
                    config,
                    position,
                    overview,
                    attendance_field_id
                FROM
                    event_layout_field
                WHERE layout_id = :layout_id
                ORDER BY position;""")
                .single(call().bind("layout_id", layoutId))
                .map(EventLayoutField.map())
                .all();
    }

    public void replaceLayoutFields(int layoutId, List<LayoutFieldEntry> fields) {
        query("DELETE FROM event_layout_field WHERE layout_id = :layout_id;")
                .single(call().bind("layout_id", layoutId))
                .delete();
        for (int i = 0; i < fields.size(); i++) {
            var f = fields.get(i);
            query("""
                         INSERT
                         INTO
                             event_layout_field(layout_id, name, field_type, config, position, overview, attendance_field_id)
                         VALUES
                             (:layout_id, :name, :field_type, :config::JSONB, :position, :overview, :attendance_field_id);""")
                    .single(call().bind("layout_id", layoutId)
                            .bind("name", f.name())
                            .bind("field_type", f.fieldType() != null ? f.fieldType() : EventFieldType.STRING)
                            .bind("config", f.config() != null ? f.config().toJson() : "{}")
                            .bind("position", i)
                            .bind("overview", f.overview())
                            .bind("attendance_field_id", f.attendanceFieldId()))
                    .insert();
        }
    }
}
