/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Where a self-check task stands. The task follows from its rows rather than being set apart from
 * them: it is done once none of them is outstanding any more.
 */
public enum SelfCheckState {
    /**
     * The member may still answer and may still change what they said.
     */
    OPEN,
    /**
     * The member has handed it in and a reviewer has not finished with it.
     */
    SUBMITTED,
    /**
     * Every row has been taken or refused and the task asks for nothing further.
     */
    DONE,
    /**
     * A checker walked the member instead. What the member said is kept and none of it is applied.
     */
    OVERTAKEN
}
