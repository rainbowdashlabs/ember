/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.entity.MemberTablePreset;
import dev.chojo.ember.feature.members.repository.MemberTablePresetRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * The selections of register columns a station saved under a name.
 */
@Singleton
public class MemberTablePresetService {
    private final MemberTablePresetRepository repository;

    @Inject
    public MemberTablePresetService(MemberTablePresetRepository repository) {
        this.repository = repository;
    }

    /**
     * Only the columns that are well formed: a column naming nothing it could show is dropped
     * rather than saved or drawn.
     *
     * @param columns the columns as the client sent them, or null
     * @return the columns worth keeping
     */
    public static List<MemberTableColumn> wellFormed(List<MemberTableColumn> columns) {
        if (columns == null) return List.of();
        return columns.stream().filter(MemberTableColumn::isWellFormed).toList();
    }

    public List<MemberTablePreset> list(int stationId) {
        return repository.findByStation(stationId);
    }

    public Optional<MemberTablePreset> findById(int id) {
        return repository.findById(id);
    }

    /**
     * Saves a selection under a name, writing over one saved under that name before.
     *
     * @param stationId the station
     * @param name      the name, trimmed before it is saved
     * @param columns   the columns
     * @return the saved selection
     */
    public MemberTablePreset save(int stationId, String name, List<MemberTableColumn> columns) {
        if (name == null || name.isBlank()) {
            throw MemberRefusal.MEMBER_TABLE_PRESET_NAME_MISSING.raise();
        }
        return repository.save(stationId, name.trim(), wellFormed(columns));
    }

    public void delete(int id, int stationId) {
        repository.delete(id, stationId);
    }
}
