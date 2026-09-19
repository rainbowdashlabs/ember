/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The column selections a station saves, and what a name means for them.
 *
 * <p>A selection is read and written whole, so these are about the whole: that it comes back as it
 * went in, that saving the same name again is somebody changing their mind rather than making a
 * second one, and that a station cannot reach another's.
 */
class MemberTablePresetRepositoryTest extends RepositoryTestBase {
    private static MemberTablePresetRepository presets;
    private static Station station;
    private static Station other;

    @BeforeAll
    static void setup() {
        presets = new MemberTablePresetRepository();
        station = stationRepo.create("PresetStation");
        other = stationRepo.create("PresetOtherStation");
    }

    private static List<MemberTableColumn> someColumns() {
        return List.of(MemberTableColumn.builtin("name"), MemberTableColumn.profileField(42));
    }

    @Test
    void aSelectionComesBackAsItWentIn() {
        var saved = presets.save(station.id(), "Abendliste", someColumns());

        assertEquals("Abendliste", saved.name());
        assertEquals(2, saved.columns().size());
        assertEquals("name", saved.columns().getFirst().key());
        assertEquals(42, saved.columns().get(1).fieldId());

        var read = presets.findById(saved.id()).orElseThrow();
        assertEquals(saved.columns(), read.columns(), "what was written down is what is read back");
    }

    /** Saving the same name twice is a change of mind, not a second selection with the same name. */
    @Test
    void savingTheSameNameWritesOverIt() {
        presets.save(station.id(), "Einsatzliste", someColumns());
        var again = presets.save(station.id(), "Einsatzliste", List.of(MemberTableColumn.builtin("groups")));

        var byStation = presets.findByStation(station.id()).stream()
                .filter(preset -> "Einsatzliste".equals(preset.name()))
                .toList();
        assertEquals(1, byStation.size(), "one name, one selection");
        assertEquals(1, again.columns().size(), "and it holds what was saved last");
        assertEquals("groups", again.columns().getFirst().key());
    }

    /** A selection belongs to its station, which is what the listing is for. */
    @Test
    void aStationSeesOnlyItsOwn() {
        presets.save(station.id(), "Nur hier", someColumns());
        var theirs = presets.findByStation(other.id());

        assertTrue(
                theirs.stream().noneMatch(preset -> "Nur hier".equals(preset.name())),
                "another station's selection is not on this station's list");
    }

    /**
     * Throwing one away names the station in the statement.
     *
     * <p>So an id belonging to somebody else does nothing at all, whether or not whoever asked
     * remembered to check first.
     */
    @Test
    void onlyTheOwningStationCanThrowOneAway() {
        var saved = presets.save(station.id(), "Zum Löschen", someColumns());

        assertFalse(presets.delete(saved.id(), other.id()), "another station's press does nothing");
        assertTrue(presets.findById(saved.id()).isPresent(), "and the selection is still there");

        assertTrue(presets.delete(saved.id(), station.id()));
        assertTrue(presets.findById(saved.id()).isEmpty());
    }

    /** A selection that names nothing is still a selection, and reads back as an empty one. */
    @Test
    void anEmptySelectionIsKept() {
        var saved = presets.save(station.id(), "Leer", List.of());

        assertTrue(presets.findById(saved.id()).orElseThrow().columns().isEmpty());
    }
}
