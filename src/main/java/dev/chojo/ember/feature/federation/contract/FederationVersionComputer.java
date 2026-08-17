/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.chojo.ember.feature.federation.entity.FederationContract;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;

/**
 * Computes the per-surface version hashes of the federation contract declared in
 * {@link FederationContractCatalog}.
 * <p>
 * Each surface hashes the sorted union of its endpoint signatures (method, path, request
 * and response types), the signatures of every record and enum transitively reachable from
 * those payload types within the project namespace, and the surface's manual revision tags.
 * Any change to what travels over a surface rolls that surface's hash - and only that one.
 */
public final class FederationVersionComputer {

    /**
     * Manual revision tag for the request signing envelope, which the type system cannot
     * see; bump it when {@code FederationSigningService.sign}/{@code verify} change shape.
     * Feature surfaces get their own tag lines the day one needs a semantic-only break.
     */
    private static final String CORE_REVISION = "signing.v2";

    private static final String PROJECT_PACKAGE = "dev.chojo.ember";
    private static final int HASH_LENGTH = 16;

    private FederationVersionComputer() {}

    /**
     * Computes the contract vector of this build: the core hash plus one hash per feature
     * surface, keyed by capability name.
     */
    public static FederationContract computeContract() {
        var features = new TreeMap<String, String>();
        for (var surface : FederationSurface.values()) {
            if (surface == FederationSurface.CORE) continue;
            features.put(surface.name(), surfaceHash(surface));
        }
        return new FederationContract(surfaceHash(FederationSurface.CORE), features);
    }

    private static String surfaceHash(FederationSurface surface) {
        var lines = new ArrayList<String>();
        var reachable = new LinkedHashSet<Class<?>>();
        for (var endpoint : FederationContractCatalog.ENDPOINTS) {
            if (endpoint.surface() != surface) continue;
            lines.add(endpoint.signatureLine());
            collectReachable(endpoint.requestType(), reachable);
            collectReachable(endpoint.responseType(), reachable);
        }
        for (var clazz : reachable) {
            lines.add(typeLine(clazz));
        }
        if (surface == FederationSurface.CORE) {
            lines.add("revision:" + CORE_REVISION);
        }
        lines.sort(String::compareTo);
        return sha256(String.join("\n", lines)).substring(0, HASH_LENGTH);
    }

    /**
     * Walks the payload type closure: project records recurse into their component types
     * (through generics like {@code List<X>} and arrays), enums and non-record project
     * types are leaves. Types outside the project namespace are contract-stable by
     * assumption and appear only inside the signatures that reference them.
     */
    private static void collectReachable(Type type, Set<Class<?>> reachable) {
        switch (type) {
            case Class<?> clazz -> {
                if (clazz.isArray()) {
                    collectReachable(clazz.componentType(), reachable);
                    return;
                }
                if (!clazz.getName().startsWith(PROJECT_PACKAGE) || !reachable.add(clazz)) return;
                if (clazz.isRecord()) {
                    for (var component : clazz.getRecordComponents()) {
                        collectReachable(component.getGenericType(), reachable);
                    }
                }
            }
            case ParameterizedType parameterized -> {
                collectReachable(parameterized.getRawType(), reachable);
                for (var argument : parameterized.getActualTypeArguments()) {
                    collectReachable(argument, reachable);
                }
            }
            case GenericArrayType array -> collectReachable(array.getGenericComponentType(), reachable);
            case WildcardType wildcard -> {
                for (var bound : wildcard.getUpperBounds()) {
                    collectReachable(bound, reachable);
                }
            }
            case TypeVariable<?> variable -> {
                for (var bound : variable.getBounds()) {
                    collectReachable(bound, reachable);
                }
            }
            default -> {}
        }
    }

    private static String typeLine(Class<?> clazz) {
        var name = clazz.getCanonicalName();
        if (clazz.isEnum()) {
            var constants = Arrays.stream(clazz.getEnumConstants())
                    .map(constant -> ((Enum<?>) constant).name())
                    .sorted()
                    .toList();
            return "enum:" + name + ":" + String.join(",", constants);
        }
        if (clazz.isRecord()) {
            var components = Arrays.stream(clazz.getRecordComponents())
                    .map(component -> component.getGenericType().getTypeName() + " " + component.getName())
                    .sorted()
                    .toList();
            return "record:" + name + ":" + String.join(",", components);
        }
        return "opaque:" + name;
    }

    private static String sha256(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Entry point for the Gradle generateFederationVersion task.
     * <p>
     * Args: {@code <contract-json-file> <history-json-file> <app-version> [frontend-history-copy]}
     * <p>
     * Writes the current contract vector to the contract file and updates the history JSON,
     * which maps hash → app version per surface so a reader (and a release diff) sees which
     * feature's contract rolled. Only one build per app version is ever released, so entries
     * of the current app version are replaced rather than accumulated: hashes unchanged
     * since an earlier version keep the version they first appeared in, while intermediate
     * hashes from the current development cycle are pruned.
     */
    static void main(String[] args) throws Exception {
        var mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        var contract = computeContract();
        var appVersion = args[2];

        var contractFile = new LinkedHashMap<String, Object>();
        contractFile.put("core", contract.core());
        contractFile.put("features", contract.features());
        mapper.writeValue(Path.of(args[0]).toFile(), contractFile);

        var historyPath = Path.of(args[1]);
        LinkedHashMap<String, LinkedHashMap<String, String>> history = Files.exists(historyPath)
                ? mapper.readValue(historyPath.toFile(), new TypeReference<>() {})
                : new LinkedHashMap<>();
        record SurfaceHash(String surface, String hash) {}
        var current = new ArrayList<SurfaceHash>();
        current.add(new SurfaceHash(FederationSurface.CORE.name(), contract.core()));
        contract.features().forEach((surface, hash) -> current.add(new SurfaceHash(surface, hash)));
        for (var entry : current) {
            var surfaceHistory = history.computeIfAbsent(entry.surface(), surface -> new LinkedHashMap<>());
            surfaceHistory.values().removeIf(appVersion::equals);
            surfaceHistory.putIfAbsent(entry.hash(), appVersion);
        }
        mapper.writeValue(historyPath.toFile(), history);

        if (args.length > 3) {
            mapper.writeValue(Path.of(args[3]).toFile(), history);
        }

        System.out.println("Federation contract: core " + contract.core() + ", " + contract.features());
    }
}
