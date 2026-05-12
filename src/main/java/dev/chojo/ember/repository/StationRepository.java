/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.entity.Station;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class StationRepository {

    private static final String STATION_COLUMNS = "id, name, timezone, locale";

    public Optional<Station> findById(int id) {
        return Query.query("SELECT " + STATION_COLUMNS + " FROM station WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(Station.map())
                .first();
    }

    public List<Station> findAll() {
        return Query.query("SELECT " + STATION_COLUMNS + " FROM station;")
                .single()
                .map(Station.map())
                .all();
    }

    public Station create(String name) {
        return Query.query("INSERT INTO station(name) VALUES(:name) RETURNING " + STATION_COLUMNS + ";")
                .single(Call.of().bind("name", name))
                .map(Station.map())
                .first()
                .orElseThrow();
    }

    public boolean update(int id, String name) {
        return Query.query("UPDATE station SET name = :name WHERE id = :id;")
                .single(Call.of().bind("name", name).bind("id", id))
                .update()
                .changed();
    }

    public boolean updateTimezone(int id, String timezone) {
        return Query.query("UPDATE station SET timezone = :timezone WHERE id = :id;")
                .single(Call.of().bind("timezone", timezone).bind("id", id))
                .update()
                .changed();
    }

    public boolean updateLocale(int id, String locale) {
        return Query.query("UPDATE station SET locale = :locale WHERE id = :id;")
                .single(Call.of().bind("locale", locale).bind("id", id))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return Query.query("DELETE FROM station WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public Optional<StationLogo> findLogo(int id) {
        return Query.query("SELECT logo, logo_content_type FROM station WHERE id = :id AND logo IS NOT NULL;")
                .single(Call.of().bind("id", id))
                .map(row -> new StationLogo(row.getBytes("logo"), row.getString("logo_content_type")))
                .first();
    }

    public boolean updateLogo(int id, byte[] logo, String contentType) {
        return Query.query("UPDATE station SET logo = :logo, logo_content_type = :content_type WHERE id = :id;")
                .single(Call.of()
                        .bind("logo", logo)
                        .bind("content_type", contentType)
                        .bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteLogo(int id) {
        return Query.query("UPDATE station SET logo = NULL, logo_content_type = NULL WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .update()
                .changed();
    }

    public record StationLogo(byte[] data, String contentType) {}
}
