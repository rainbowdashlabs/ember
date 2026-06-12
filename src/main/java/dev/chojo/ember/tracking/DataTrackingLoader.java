/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads and writes {@link DataTracking} from/to JSON.
 * Maps are written with sorted keys so git diffs stay clean.
 */
public final class DataTrackingLoader {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .addModule(instantModule())
            .build();

    private DataTrackingLoader() {}

    /**
     * Reads the tracking file from the classpath ({@link DataTracking#RESOURCE_PATH}).
     * Returns an empty tracking if not present.
     */
    public static DataTracking loadFromClasspath() throws IOException {
        try (InputStream is = DataTrackingLoader.class.getResourceAsStream(DataTracking.RESOURCE_PATH)) {
            if (is == null) return empty();
            return MAPPER.readValue(is, DataTracking.class);
        }
    }

    /**
     * Reads the tracking file from a given path. Returns an empty tracking if not present.
     */
    public static DataTracking load(Path file) throws IOException {
        if (!Files.exists(file)) return empty();
        return MAPPER.readValue(Files.newInputStream(file), DataTracking.class);
    }

    /**
     * Writes the tracking file to disk with stable map ordering and a trailing newline.
     */
    public static void write(Path file, DataTracking tracking) throws IOException {
        var sorted = withSortedMaps(tracking);
        String json = MAPPER.writeValueAsString(sorted) + System.lineSeparator();
        Files.createDirectories(file.getParent());
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }

    /**
     * Returns an empty DataTracking with no tables or file stores.
     */
    public static DataTracking empty() {
        return new DataTracking(
                DataTracking.CURRENT_VERSION, null, Instant.now(), new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    private static DataTracking withSortedMaps(DataTracking tracking) {
        Map<String, TableEntry> sortedTables =
                tracking.tables() == null ? new TreeMap<>() : new TreeMap<>(tracking.tables());
        Map<String, FileStoreEntry> sortedFileStores =
                tracking.fileStores() == null ? new TreeMap<>() : new TreeMap<>(tracking.fileStores());
        return new DataTracking(
                tracking.version(), tracking.schemaHash(), tracking.generatedAt(), sortedTables, sortedFileStores);
    }

    private static SimpleModule instantModule() {
        var module = new SimpleModule();
        module.addSerializer(Instant.class, new InstantSerializer());
        return module;
    }

    /**
     * Serializes Instants as ISO-8601 UTC strings without subsecond noise.
     */
    private static final class InstantSerializer extends StdSerializer<Instant> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;

        InstantSerializer() {
            super(Instant.class);
        }

        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializationContext ctx) {
            gen.writeString(value.truncatedTo(ChronoUnit.SECONDS)
                    .atOffset(ZoneOffset.UTC)
                    .format(FORMATTER));
        }
    }
}
