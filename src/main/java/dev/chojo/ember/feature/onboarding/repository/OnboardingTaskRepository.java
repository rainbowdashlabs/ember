/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.onboarding.repository;

import dev.chojo.ember.feature.onboarding.entity.OnboardingMark;
import jakarta.inject.Singleton;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The marks somebody left on onboarding tasks. Holds nothing that can be derived, so a row here is
 * always a statement by a person: ticked off, or passed over.
 */
@Singleton
public class OnboardingTaskRepository {

    public List<OnboardingMark> findByMember(int memberId) {
        return query("""
                SELECT task_key, state, changed_at
                FROM onboarding_member_task
                WHERE member_id = :member_id;""")
                .single(call().bind("member_id", memberId))
                .map(OnboardingMark.map(null))
                .all();
    }

    public void markForMember(int memberId, String taskKey, String state) {
        query("""
                INSERT INTO onboarding_member_task(member_id, task_key, state)
                VALUES (:member_id, :task_key, :state)
                ON CONFLICT (member_id, task_key)
                    DO UPDATE SET state = excluded.state, changed_at = now();""")
                .single(call().bind("member_id", memberId)
                        .bind("task_key", taskKey)
                        .bind("state", state))
                .update();
    }

    public void clearForMember(int memberId, String taskKey) {
        query("DELETE FROM onboarding_member_task WHERE member_id = :member_id AND task_key = :task_key;")
                .single(call().bind("member_id", memberId).bind("task_key", taskKey))
                .update();
    }

    public List<OnboardingMark> findByStation(int stationId) {
        return query("""
                SELECT task_key, state, changed_at, changed_by_member
                FROM onboarding_station_task
                WHERE station_id = :station_id;""")
                .single(call().bind("station_id", stationId))
                .map(OnboardingMark.map("changed_by_member"))
                .all();
    }

    public void markForStation(int stationId, String taskKey, String state, int actorMemberId) {
        query("""
                INSERT INTO onboarding_station_task(station_id, task_key, state, changed_by_member)
                VALUES (:station_id, :task_key, :state, :actor)
                ON CONFLICT (station_id, task_key)
                    DO UPDATE SET state = excluded.state, changed_at = now(),
                                  changed_by_member = excluded.changed_by_member;""")
                .single(call().bind("station_id", stationId)
                        .bind("task_key", taskKey)
                        .bind("state", state)
                        .bind("actor", actorMemberId))
                .update();
    }

    public void clearForStation(int stationId, String taskKey) {
        query("DELETE FROM onboarding_station_task WHERE station_id = :station_id AND task_key = :task_key;")
                .single(call().bind("station_id", stationId).bind("task_key", taskKey))
                .update();
    }

    public List<OnboardingMark> findForInstance() {
        return query("SELECT task_key, state, changed_at, changed_by_account FROM onboarding_instance_task;")
                .single()
                .map(OnboardingMark.map("changed_by_account"))
                .all();
    }

    public void markForInstance(String taskKey, String state, int actorAccountId) {
        query("""
                INSERT INTO onboarding_instance_task(task_key, state, changed_by_account)
                VALUES (:task_key, :state, :actor)
                ON CONFLICT (task_key)
                    DO UPDATE SET state = excluded.state, changed_at = now(),
                                  changed_by_account = excluded.changed_by_account;""")
                .single(call().bind("task_key", taskKey).bind("state", state).bind("actor", actorAccountId))
                .update();
    }

    public void clearForInstance(String taskKey) {
        query("DELETE FROM onboarding_instance_task WHERE task_key = :task_key;")
                .single(call().bind("task_key", taskKey))
                .update();
    }
}
