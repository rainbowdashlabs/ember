/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementFlow;
import dev.chojo.ember.feature.inventory.entity.MovementFlowBinding;
import dev.chojo.ember.feature.inventory.entity.MovementFlowStep;
import dev.chojo.ember.feature.inventory.entity.MovementParty;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for movement flows, their steps and the bindings that say which flow applies.
 */
@Singleton
public class MovementFlowRepository {
    private static final String FLOW_COLUMNS = "id, station_id, cluster_id, name, purpose, archived";
    private static final String STEP_COLUMNS =
            "id, flow_id, position, label, actor, subject, custody_after, picks_item, archived";
    private static final String BINDING_COLUMNS = "station_id, inventory_id, owner_kind, purpose, party, flow_id";

    // -- Flows --

    public Optional<MovementFlow> findFlowById(int id) {
        return SqlSupport.findById("movement_flow", FLOW_COLUMNS, id, MovementFlow.map());
    }

    /**
     * Every flow a station has, retired ones included, so the configuration screen can show what a
     * finished movement was walked under.
     */
    public List<MovementFlow> findFlowsByStation(int stationId) {
        return query("""
                SELECT %s FROM movement_flow WHERE station_id = :station_id ORDER BY purpose, name;""", FLOW_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(MovementFlow.map())
                .all();
    }

    public MovementFlow createFlow(int stationId, String name, MovementPurpose purpose) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO movement_flow(station_id, name, purpose)
                VALUES (:station_id, :name, :purpose)
                RETURNING %s;""",
                call().bind("station_id", stationId).bind("name", name).bind("purpose", purpose),
                MovementFlow.map(),
                FLOW_COLUMNS);
    }

    public boolean renameFlow(int id, String name) {
        return query("UPDATE movement_flow SET name = :name WHERE id = :id;")
                .single(call().bind("name", name).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Retires a flow. Flows are archived rather than deleted so a movement that walked one still
     * reads with the words it was walked under.
     */
    public boolean archiveFlow(int id) {
        return query("UPDATE movement_flow SET archived = TRUE WHERE id = :id;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    // -- Steps --

    public Optional<MovementFlowStep> findStepById(int id) {
        return SqlSupport.findById("movement_flow_step", STEP_COLUMNS, id, MovementFlowStep.map());
    }

    /**
     * The steps a new movement on this flow will walk, in order. Retired steps are left out,
     * because they are exactly the ones a new movement should not walk.
     */
    public List<MovementFlowStep> findActiveSteps(int flowId) {
        return query("""
                SELECT %s FROM movement_flow_step
                WHERE flow_id = :flow_id AND NOT archived
                ORDER BY position;""", STEP_COLUMNS)
                .single(call().bind("flow_id", flowId))
                .map(MovementFlowStep.map())
                .all();
    }

    /**
     * Every step of a flow including the retired ones, for rendering a movement that passed through
     * one of them.
     */
    public List<MovementFlowStep> findAllSteps(int flowId) {
        return query("""
                SELECT %s FROM movement_flow_step WHERE flow_id = :flow_id ORDER BY position;""", STEP_COLUMNS)
                .single(call().bind("flow_id", flowId))
                .map(MovementFlowStep.map())
                .all();
    }

    public MovementFlowStep createStep(
            int flowId,
            int position,
            String label,
            StepActor actor,
            StepSubject subject,
            ItemCustody custodyAfter,
            boolean picksItem) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO movement_flow_step(flow_id, position, label, actor, subject, custody_after, picks_item)
                VALUES (:flow_id, :position, :label, :actor, :subject, :custody_after, :picks_item)
                RETURNING %s;""",
                call().bind("flow_id", flowId)
                        .bind("position", position)
                        .bind("label", label)
                        .bind("actor", actor)
                        .bind("subject", subject)
                        .bind("custody_after", custodyAfter)
                        .bind("picks_item", picksItem),
                MovementFlowStep.map(),
                STEP_COLUMNS);
    }

    public boolean updateStep(
            int id, String label, StepActor actor, StepSubject subject, ItemCustody custodyAfter, boolean picksItem) {
        return query("""
                UPDATE movement_flow_step
                SET label         = :label,
                    actor         = :actor,
                    subject       = :subject,
                    custody_after = :custody_after,
                    picks_item    = :picks_item
                WHERE id = :id;""")
                .single(call().bind("label", label)
                        .bind("actor", actor)
                        .bind("subject", subject)
                        .bind("custody_after", custodyAfter)
                        .bind("picks_item", picksItem)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Retires a step. A step in use is archived, never deleted, so it disappears from new movements
     * and still renders in the history of the ones that passed it.
     */
    public boolean archiveStep(int id) {
        return query("UPDATE movement_flow_step SET archived = TRUE WHERE id = :id;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    /**
     * The position after the last one a flow uses, so a new step lands at the end without colliding
     * with a retired step that still holds its place.
     */
    public int nextStepPosition(int flowId) {
        return SqlSupport.count(
                "SELECT coalesce(max(position), -1) + 1 FROM movement_flow_step WHERE flow_id = :flow_id;",
                call().bind("flow_id", flowId));
    }

    // -- Bindings --

    /**
     * The flow a station uses for an owner and a purpose. A binding naming the inventory wins over
     * the station-wide one, which is what lets one mixed inventory reach different flows for
     * different rows.
     *
     * @param stationId   the station
     * @param inventoryId the inventory the movement is about
     * @param ownerKind   who owns the item
     * @param purpose     what the movement is for
     * @return the bound flow, or empty when the station has none for that pair
     */
    public Optional<Integer> findBoundFlow(
            int stationId, Integer inventoryId, ItemOwner ownerKind, MovementPurpose purpose, MovementParty party) {
        return query("""
                SELECT flow_id FROM movement_flow_binding
                WHERE station_id = :station_id
                  AND owner_kind = :owner_kind
                  AND purpose = :purpose
                  AND party = :party
                  AND (inventory_id = :inventory_id OR inventory_id IS NULL)
                ORDER BY inventory_id NULLS LAST
                LIMIT 1;""")
                .single(call().bind("station_id", stationId)
                        .bind("inventory_id", inventoryId)
                        .bind("owner_kind", ownerKind)
                        .bind("purpose", purpose)
                        .bind("party", party))
                .map(row -> row.getInt("flow_id"))
                .first();
    }

    /** Whether any binding points at this chain, which is what makes it one somebody could walk. */
    public boolean isBound(int flowId) {
        return query("SELECT 1 FROM movement_flow_binding WHERE flow_id = :flow_id LIMIT 1;")
                .single(call().bind("flow_id", flowId))
                .map(row -> true)
                .first()
                .orElse(false);
    }

    /**
     * How far positions are lifted out of the way while a chain is being reordered. Above any step
     * count a chain will ever have, so the parked positions cannot collide with the final ones.
     */
    private static final int PARKING_OFFSET = 1000;

    /**
     * Writes the order of a chain's steps.
     *
     * <p>Two statements rather than one, because a position is unique per chain and a single update
     * would put two steps on the same one halfway through. The first lifts every step clear by a
     * fixed offset, the second sets the order. Lifting keeps the steps in the order they were in, so
     * a chain caught between the two still reads as the chain it was rather than as a reversed one.
     *
     * <p>The live steps take the positions they already occupied between them, so a retired step
     * keeps the place it holds and does not sort to the end of a chain it was once in the middle of.
     *
     * @param flowId    the chain
     * @param stepIds   its live steps, in the order they are to be walked
     * @param positions the positions those steps hold today, ascending
     */
    public void applyStepOrder(int flowId, List<Integer> stepIds, List<Integer> positions) {
        query("UPDATE movement_flow_step SET position = position + :offset WHERE flow_id = :flow_id AND NOT archived;")
                .single(call().bind("offset", PARKING_OFFSET).bind("flow_id", flowId))
                .update();
        query("""
                UPDATE movement_flow_step
                SET position = ordered.position
                FROM unnest(CAST(:step_ids AS INT[]), CAST(:positions AS INT[])) AS ordered(step_id, position)
                WHERE movement_flow_step.id = ordered.step_id
                  AND movement_flow_step.flow_id = :flow_id;""")
                .single(call().bind("flow_id", flowId)
                        .bind("step_ids", List.copyOf(stepIds), PostgreSqlTypes.INTEGER)
                        .bind("positions", List.copyOf(positions), PostgreSqlTypes.INTEGER))
                .update();
    }

    /**
     * A cluster's own binding for a purpose, which outranks the station's when the cluster owns the gear.
     *
     * <p>No inventory in the key. A station binds per inventory because it may keep several with different
     * habits; a cluster's gear is one pool, so one answer per purpose is the whole of it.
     *
     * @param clusterId the owning cluster
     * @param purpose   what the movement is for
     * @return the flow to walk, or empty when the cluster has bound none
     */
    public Optional<Integer> findClusterBoundFlow(int clusterId, MovementPurpose purpose) {
        return query("""
                SELECT id FROM movement_flow
                WHERE cluster_id = :cluster_id AND purpose = :purpose AND NOT archived
                ORDER BY id
                LIMIT 1;""")
                .single(call().bind("cluster_id", clusterId).bind("purpose", purpose))
                .map(row -> row.getInt("id"))
                .first();
    }

    /**
     * The flows a cluster keeps, whatever their purpose.
     *
     * @param clusterId the cluster
     * @return its flows, newest last
     */
    public List<MovementFlow> findClusterFlows(int clusterId) {
        return query("""
                SELECT %s FROM movement_flow
                WHERE cluster_id = :cluster_id AND NOT archived
                ORDER BY purpose, id;""", FLOW_COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(MovementFlow.map())
                .all();
    }

    /**
     * Creates a flow the cluster owns rather than a station.
     *
     * @param clusterId the cluster
     * @param name      what it is called
     * @param purpose   what it is for
     * @return the flow
     */
    public MovementFlow createClusterFlow(int clusterId, String name, MovementPurpose purpose) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO movement_flow(cluster_id, name, purpose)
                VALUES (:cluster_id, :name, :purpose)
                RETURNING %s;""",
                call().bind("cluster_id", clusterId).bind("name", name).bind("purpose", purpose),
                MovementFlow.map(),
                FLOW_COLUMNS);
    }

    public List<MovementFlowBinding> findBindings(int stationId) {
        return query("""
                SELECT %s FROM movement_flow_binding
                WHERE station_id = :station_id
                ORDER BY inventory_id NULLS FIRST, owner_kind, purpose, party;""", BINDING_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(MovementFlowBinding.map())
                .all();
    }

    /**
     * Points a binding at a flow only where nothing points there yet.
     *
     * <p>What the seeding uses. Two requests arriving together both find a combination unbound and
     * both write it, and the second one used to end the request with an error the reader could do
     * nothing about. The one that gets there second is told so here instead.
     *
     * @return whether this call is the one that bound it
     */
    public boolean bindIfAbsent(
            int stationId,
            Integer inventoryId,
            ItemOwner ownerKind,
            MovementPurpose purpose,
            MovementParty party,
            int flowId) {
        return query("""
                INSERT INTO movement_flow_binding(station_id, inventory_id, owner_kind, purpose, party, flow_id)
                VALUES (:station_id, :inventory_id, :owner_kind, :purpose, :party, :flow_id)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("station_id", stationId)
                        .bind("inventory_id", inventoryId)
                        .bind("owner_kind", ownerKind)
                        .bind("purpose", purpose)
                        .bind("party", party)
                        .bind("flow_id", flowId))
                .insert()
                .changed();
    }

    /** Removes a flow and its steps outright, which is only ever a seeding that lost a race. */
    public boolean deleteFlow(int id) {
        return SqlSupport.deleteById("movement_flow", id);
    }

    /**
     * Points a binding at a flow, replacing whatever it pointed at before.
     */
    public void bind(
            int stationId,
            Integer inventoryId,
            ItemOwner ownerKind,
            MovementPurpose purpose,
            MovementParty party,
            int flowId) {
        query("""
                DELETE FROM movement_flow_binding
                WHERE station_id = :station_id
                  AND owner_kind = :owner_kind
                  AND purpose = :purpose
                  AND party = :party
                  AND inventory_id IS NOT DISTINCT FROM :inventory_id;""")
                .single(call().bind("station_id", stationId)
                        .bind("inventory_id", inventoryId)
                        .bind("owner_kind", ownerKind)
                        .bind("purpose", purpose)
                        .bind("party", party))
                .delete();
        query("""
                INSERT INTO movement_flow_binding(station_id, inventory_id, owner_kind, purpose, party, flow_id)
                VALUES (:station_id, :inventory_id, :owner_kind, :purpose, :party, :flow_id);""")
                .single(call().bind("station_id", stationId)
                        .bind("inventory_id", inventoryId)
                        .bind("owner_kind", ownerKind)
                        .bind("purpose", purpose)
                        .bind("party", party)
                        .bind("flow_id", flowId))
                .insert();
    }

    /**
     * Whether a station has any flow at all, which is how the preset seeding tells a station that
     * has never been set up from one that has.
     */
    public boolean hasAnyFlow(int stationId) {
        return SqlSupport.exists(
                "SELECT 1 FROM movement_flow WHERE station_id = :station_id LIMIT 1;",
                call().bind("station_id", stationId));
    }
}
