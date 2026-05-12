/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.entity.StationApplication;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class StationApplicationRepository {

    public StationApplication create(
            String firstName,
            String lastName,
            String email,
            String stationName,
            String introduction,
            String verificationToken) {
        return Query.query("""
                            INSERT INTO station_application(first_name, last_name, email, station_name, introduction, verification_token)
                            VALUES (:first_name, :last_name, :email, :station_name, :introduction, :verification_token)
                            RETURNING id, first_name, last_name, email, station_name, introduction, verification_token, status, deny_reason, created_at, resolved_at;""")
                .single(Call.of()
                        .bind("first_name", firstName)
                        .bind("last_name", lastName)
                        .bind("email", email)
                        .bind("station_name", stationName)
                        .bind("introduction", introduction != null ? introduction : "")
                        .bind("verification_token", verificationToken))
                .map(StationApplication.map())
                .first()
                .orElseThrow();
    }

    public Optional<StationApplication> findById(int id) {
        return Query.query(
                        "SELECT id, first_name, last_name, email, station_name, introduction, verification_token, status, deny_reason, created_at, resolved_at FROM station_application WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(StationApplication.map())
                .first();
    }

    public List<StationApplication> findByStatus(String status) {
        return Query.query(
                        "SELECT id, first_name, last_name, email, station_name, introduction, verification_token, status, deny_reason, created_at, resolved_at FROM station_application WHERE status = :status ORDER BY created_at;")
                .single(Call.of().bind("status", status))
                .map(StationApplication.map())
                .all();
    }

    public List<StationApplication> findAll() {
        return Query.query(
                        "SELECT id, first_name, last_name, email, station_name, introduction, verification_token, status, deny_reason, created_at, resolved_at FROM station_application ORDER BY created_at DESC;")
                .single()
                .map(StationApplication.map())
                .all();
    }

    public Optional<StationApplication> findByToken(String token) {
        return Query.query(
                        "SELECT id, first_name, last_name, email, station_name, introduction, verification_token, status, deny_reason, created_at, resolved_at FROM station_application WHERE verification_token = :token;")
                .single(Call.of().bind("token", token))
                .map(StationApplication.map())
                .first();
    }

    public boolean verify(int id) {
        return Query.query(
                        "UPDATE station_application SET status = 'pending', verification_token = NULL WHERE id = :id AND status = 'unverified';")
                .single(Call.of().bind("id", id))
                .update()
                .changed();
    }

    public boolean accept(int id) {
        return Query.query(
                        "UPDATE station_application SET status = 'accepted', resolved_at = NOW() WHERE id = :id AND status = 'pending';")
                .single(Call.of().bind("id", id))
                .update()
                .changed();
    }

    public boolean deny(int id, String reason) {
        return Query.query(
                        "UPDATE station_application SET status = 'denied', deny_reason = :reason, resolved_at = NOW() WHERE id = :id AND status = 'pending';")
                .single(Call.of().bind("id", id).bind("reason", reason))
                .update()
                .changed();
    }
}
