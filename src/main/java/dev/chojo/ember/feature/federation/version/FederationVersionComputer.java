/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.version;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.ChangeType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationCapability;
import dev.chojo.ember.feature.federation.entity.FederationChangeLog;
import dev.chojo.ember.feature.federation.entity.FederationMetadataCache;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationShare;
import dev.chojo.ember.feature.federation.entity.InventoryBlock;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingRequestItem;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Computes a deterministic hash of the federation API contract.
 * <p>
 * The hash is derived from:
 * <ul>
 *   <li>All federation entity classes (record fields + enum constants)</li>
 *   <li>All DTO records defined in route and HTTP client classes</li>
 *   <li>All federation endpoint definitions (method + path)</li>
 * </ul>
 * <p>
 * If any of these change, the hash changes, indicating a breaking federation protocol change.
 */
public final class FederationVersionComputer {

    /**
     * All entity classes/enums in the federation entity package.
     */
    private static final List<Class<?>> ENTITY_CLASSES = List.of(
            CapabilityType.class,
            ChangeType.class,
            ContentType.class,
            Direction.class,
            FederationCapability.class,
            FederationChangeLog.class,
            FederationMetadataCache.class,
            FederationPartner.class,
            FederationShare.class,
            InventoryBlock.class,
            LendingMessage.class,
            LendingRequest.class,
            LendingRequestItem.class,
            LendingStatus.class,
            ShareScope.class);

    /**
     * Classes that contain inner record DTOs used in federation communication.
     */
    private static final List<Class<?>> DTO_CONTAINER_CLASSES = List.of(FederationHttpClient.class);

    private FederationVersionComputer() {}

    /**
     * Entry point for the Gradle generateFederationVersion task.
     * <p>
     * Args: {@code <current-hash-file> <history-json-file> <app-version> [frontend-history-copy]}
     * <p>
     * Writes the current hash to the hash file, and adds a new entry to the
     * history JSON (hash → app version) only if that hash is not already present.
     * If a 4th argument is provided, the history JSON is also written there for the frontend.
     */
    public static void main(String[] args) throws Exception {
        var mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        var hash = computeHash();

        // Write current hash
        Files.writeString(Path.of(args[0]), hash);

        // Read or create history
        var historyPath = Path.of(args[1]);
        var appVersion = args[2];
        LinkedHashMap<String, String> history = Files.exists(historyPath)
                ? mapper.readValue(historyPath.toFile(), new TypeReference<>() {})
                : new LinkedHashMap<>();

        // Add new hash only if absent
        history.putIfAbsent(hash, appVersion);

        mapper.writeValue(historyPath.toFile(), history);

        // Write frontend copy if path provided
        if (args.length > 3) {
            mapper.writeValue(Path.of(args[3]).toFile(), history);
        }

        System.out.println("Federation version hash: " + hash + " (version: " + history.get(hash) + ")");
    }

    /**
     * Compute the SHA-256 hash of the federation API contract.
     */
    public static String computeHash() {
        var lines = new ArrayList<String>();

        // Collect entity class signatures
        for (var clazz : ENTITY_CLASSES) {
            lines.addAll(classSignature(clazz));
        }

        // Collect inner record/enum signatures from route and HTTP client classes
        for (var container : DTO_CONTAINER_CLASSES) {
            for (var inner : container.getDeclaredClasses()) {
                if (inner.isRecord() || inner.isEnum()) {
                    lines.addAll(classSignature(inner));
                }
            }
        }

        // Endpoint definitions are no longer statically listed —
        // routes are now distributed across domain classes.
        // Version is computed from entity/record class signatures only.

        lines.sort(String::compareTo);

        var content = String.join("\n", lines);
        return sha256(content);
    }

    private static List<String> classSignature(Class<?> clazz) {
        var lines = new ArrayList<String>();
        var name = clazz.getCanonicalName();

        if (clazz.isEnum()) {
            var constants = Arrays.stream(clazz.getEnumConstants())
                    .map(e -> ((Enum<?>) e).name())
                    .sorted()
                    .toList();
            lines.add("enum:" + name + ":" + String.join(",", constants));
        } else if (clazz.isRecord()) {
            var components = Arrays.stream(clazz.getRecordComponents())
                    .map(FederationVersionComputer::componentSignature)
                    .sorted()
                    .toList();
            lines.add("record:" + name + ":" + String.join(",", components));
        }
        return lines;
    }

    private static String componentSignature(RecordComponent rc) {
        return rc.getType().getCanonicalName() + " " + rc.getName();
    }

    private static String sha256(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
