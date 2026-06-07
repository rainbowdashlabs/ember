/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import java.util.List;

public enum LanePreset {
    SIMPLE(List.of("Offen", "In Arbeit", "Erledigt")),
    FEEDBACK(List.of("Offen", "In Arbeit", "Feedback", "Erledigt"));

    private final List<String> laneNames;

    LanePreset(List<String> laneNames) {
        this.laneNames = laneNames;
    }

    public List<String> laneNames() {
        return laneNames;
    }
}
