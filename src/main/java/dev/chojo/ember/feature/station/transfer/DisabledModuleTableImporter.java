/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;

/**
 * Installs the flat list of disabled module names carried by the {@code station_disabled_module}
 * wire entry. Names this instance does not know are skipped, so a bundle from a newer source
 * still imports.
 */
@Singleton
public class DisabledModuleTableImporter implements TableImporter {
    private static final Logger log = LoggerFactory.getLogger(DisabledModuleTableImporter.class);
    private final StationRepository stationRepository;

    @Inject
    public DisabledModuleTableImporter(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    @Override
    public String table() {
        return "station_disabled_module";
    }

    @Override
    @SuppressWarnings("unchecked")
    public int importRows(StationImportContext context, Object payload) {
        List<Object> moduleNames = (List<Object>) payload;
        if (moduleNames == null || moduleNames.isEmpty()) return 0;
        var modules = new HashSet<StationModule>();
        for (Object o : moduleNames) {
            try {
                modules.add(StationModule.valueOf(o.toString()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown station module name in import payload: {}", o);
            }
        }
        stationRepository.setDisabledModules(context.stationId(), modules);
        return modules.size();
    }
}
