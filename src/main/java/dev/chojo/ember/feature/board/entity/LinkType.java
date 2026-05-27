/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

public enum LinkType {
    RELATES_TO,
    BLOCKS,
    BLOCKED_BY,
    CAUSES,
    CAUSED_BY;

    public LinkType inverse() {
        return switch (this) {
            case RELATES_TO -> RELATES_TO;
            case BLOCKS -> BLOCKED_BY;
            case BLOCKED_BY -> BLOCKS;
            case CAUSES -> CAUSED_BY;
            case CAUSED_BY -> CAUSES;
        };
    }
}
