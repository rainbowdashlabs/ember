/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.feature.inventory.entity.AckKind;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemMovementLog;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for movements and the log of what was acknowledged on them.
 */
@Singleton
public class ItemMovementRepository {
    private static final String MOVEMENT_COLUMNS = """
            id, station_id, purpose, flow_id, current_step_id, member_id, outgoing_item_id, incoming_item_id, \
            inventory_id, old_size_id, new_size_id, state, reason, created_by, created_at, closed_at, close_reason, \
            lost_report""";
    private static final String LOG_COLUMNS =
            "id, movement_id, step_id, step_label, ack_kind, changed_by, changed_at, note";

    public Optional<ItemMovement> findById(int id) {
        return SqlSupport.findById("item_movement", MOVEMENT_COLUMNS, id, ItemMovement.map());
    }

    public List<ItemMovement> findByStation(int stationId) {
        return query("""
                SELECT %s FROM item_movement WHERE station_id = :station_id ORDER BY created_at DESC;""", MOVEMENT_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(ItemMovement.map())
                .all();
    }

    /**
     * The movements standing on a step the cluster has to answer.
     *
     * <p>Its queue. A cluster does not browse its stations' movements; it wants the ones waiting on it, which
     * is the current step naming the owner as the party whose turn it is.
     *
     * @param clusterId the cluster
     * @return the open movements waiting for it, oldest first, because the oldest has waited longest
     */
    public List<ItemMovement> findWaitingForCluster(int clusterId) {
        return query("""
                SELECT %s FROM item_movement m
                JOIN movement_flow_step step ON step.id = m.current_step_id
                JOIN station s ON s.id = m.station_id
                WHERE m.state = :open
                  AND step.actor = :owner
                  AND s.cluster_id = :cluster_id
                ORDER BY m.created_at;""", SqlSupport.alias("m", MOVEMENT_COLUMNS))
                .single(call().bind("cluster_id", clusterId)
                        .bind("open", MovementState.OPEN)
                        .bind("owner", StepActor.OWNER))
                .map(ItemMovement.map())
                .all();
    }

    public List<ItemMovement> findByMember(int memberId) {
        return query("""
                SELECT %s FROM item_movement WHERE member_id = :member_id ORDER BY created_at DESC;""", MOVEMENT_COLUMNS)
                .single(call().bind("member_id", memberId))
                .map(ItemMovement.map())
                .all();
    }

    /**
     * Whether any movement that is still walking its flow uses this flow. Reordering or removing a
     * step is refused while one does, because the movement would find itself somewhere else.
     */
    public boolean hasOpenMovementOnFlow(int flowId) {
        return SqlSupport.exists(
                "SELECT 1 FROM item_movement WHERE flow_id = :flow_id AND state = :open LIMIT 1;",
                call().bind("flow_id", flowId).bind("open", MovementState.OPEN));
    }

    public int countOpenByStation(int stationId) {
        return SqlSupport.count(
                "SELECT count(*) FROM item_movement WHERE station_id = :station_id AND state = :open;",
                call().bind("station_id", stationId).bind("open", MovementState.OPEN));
    }

    public ItemMovement create(
            int stationId,
            MovementPurpose purpose,
            int flowId,
            Integer currentStepId,
            Integer memberId,
            Integer outgoingItemId,
            Integer inventoryId,
            Integer oldSizeId,
            Integer newSizeId,
            String reason,
            Integer createdBy,
            boolean lostReport) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO item_movement(station_id, purpose, flow_id, current_step_id, member_id, outgoing_item_id,
                                          inventory_id, old_size_id, new_size_id, reason, created_by, lost_report)
                VALUES (:station_id, :purpose, :flow_id, :current_step_id, :member_id, :outgoing_item_id,
                        :inventory_id, :old_size_id, :new_size_id, :reason, :created_by, :lost_report)
                RETURNING %s;""",
                call().bind("lost_report", lostReport)
                        .bind("station_id", stationId)
                        .bind("purpose", purpose)
                        .bind("flow_id", flowId)
                        .bind("current_step_id", currentStepId)
                        .bind("member_id", memberId)
                        .bind("outgoing_item_id", outgoingItemId)
                        .bind("inventory_id", inventoryId)
                        .bind("old_size_id", oldSizeId)
                        .bind("new_size_id", newSizeId)
                        .bind("reason", reason != null ? reason : "")
                        .bind("created_by", createdBy),
                ItemMovement.map(),
                MOVEMENT_COLUMNS);
    }

    /**
     * Moves a movement onto its next step, or off the end of its flow.
     *
     * @param id     the movement
     * @param stepId the step it now stands on, or {@code null} when it has reached the end
     */
    public boolean moveToStep(int id, Integer stepId) {
        return query("UPDATE item_movement SET current_step_id = :step_id WHERE id = :id;")
                .single(call().bind("step_id", stepId).bind("id", id))
                .update()
                .changed();
    }

    public boolean setIncomingItem(int id, Integer itemId) {
        return query("UPDATE item_movement SET incoming_item_id = :item_id WHERE id = :id;")
                .single(call().bind("item_id", itemId).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Closes a movement, whichever way it ended. The step it was standing on is cleared, because a
     * closed movement stands on nothing.
     */
    public boolean close(int id, MovementState state, String closeReason) {
        return query("""
                UPDATE item_movement
                SET state           = :state,
                    current_step_id = NULL,
                    closed_at       = :closed_at,
                    close_reason    = :close_reason
                WHERE id = :id;""")
                .single(call().bind("state", state)
                        .bind("closed_at", Instant.now(), INSTANT_TIMESTAMP)
                        .bind("close_reason", closeReason)
                        .bind("id", id))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return SqlSupport.deleteById("item_movement", id);
    }

    // -- Log --

    public ItemMovementLog createLog(
            int movementId, Integer stepId, String stepLabel, AckKind ackKind, Integer changedBy, String note) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO item_movement_log(movement_id, step_id, step_label, ack_kind, changed_by, note)
                VALUES (:movement_id, :step_id, :step_label, :ack_kind, :changed_by, :note)
                RETURNING %s;""",
                call().bind("movement_id", movementId)
                        .bind("step_id", stepId)
                        .bind("step_label", stepLabel)
                        .bind("ack_kind", ackKind)
                        .bind("changed_by", changedBy)
                        .bind("note", note != null ? note : ""),
                ItemMovementLog.map(),
                LOG_COLUMNS);
    }

    public List<ItemMovementLog> findLogs(int movementId) {
        return query("""
                SELECT %s FROM item_movement_log WHERE movement_id = :movement_id ORDER BY changed_at, id;""", LOG_COLUMNS)
                .single(call().bind("movement_id", movementId))
                .map(ItemMovementLog.map())
                .all();
    }
}
