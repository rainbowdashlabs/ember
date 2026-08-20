/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * One step of a flow. A step says three things: who acknowledges it, which item it is about, and
 * what custody that item is in once it is acknowledged.
 *
 * <p>The label is free text and can be renamed without changing anything, because the behaviour
 * hangs off {@link #custodyAfter()} and {@link #subject()}, never off the words.
 *
 * @param id           the unique step identifier
 * @param flowId       the flow this step belongs to
 * @param position     where in the chain the step sits, counted from zero
 * @param label        what the step is called
 * @param actor        who acknowledges it
 * @param subject      which of the movement's two items it is about
 * @param custodyAfter the custody the subject item is in once the step is acknowledged
 * @param picksItem    whether the acknowledger names the replacement at this step
 * @param archived     whether the step is retired from new movements
 */
public record MovementFlowStep(
        int id,
        int flowId,
        int position,
        String label,
        StepActor actor,
        StepSubject subject,
        ItemCustody custodyAfter,
        boolean picksItem,
        boolean archived) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<MovementFlowStep> map() {
        return row -> new MovementFlowStep(
                row.getInt("id"),
                row.getInt("flow_id"),
                row.getInt("position"),
                row.getString("label"),
                row.getEnum("actor", StepActor.class),
                row.getEnum("subject", StepSubject.class),
                row.getEnum("custody_after", ItemCustody.class),
                row.getBoolean("picks_item"),
                row.getBoolean("archived"));
    }
}
