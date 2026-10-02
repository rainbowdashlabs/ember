/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.spec;

import dev.chojo.ember.api.spec.WireSchemas.Direction;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.ContentType;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.NULL_CLASS;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiAnnotationsKt;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.openapi.OpenApis;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The OpenAPI 3.1 description of the API: every operation the route classes annotate with
 * {@link OpenApi}, and the types they send and read as {@link WireSchemas} describes them.
 *
 * <p>The annotations say which record travels on which route and nothing else is taken from them.
 * Responses are described before request bodies, so a type that is both sent and read is described as
 * it is sent.
 */
final class ApiSpec {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final JsonMapper WRITER = JsonMapper.builder().build();

    private ApiSpec() {}

    /**
     * Describes the operations of the given route classes as the given mapper writes their types.
     *
     * @param routeClasses the route classes whose annotations index the operations
     * @param mapper       the mapper the API writes and reads with
     * @return the description as JSON text, ending with a newline
     * @throws IllegalStateException when two annotations describe one operation or two types share a name
     */
    static String render(Collection<Class<?>> routeClasses, JsonMapper mapper) {
        List<Operation> operations = operations(routeClasses);
        WireSchemas schemas = new WireSchemas(mapper);
        Map<Operation, ObjectNode> described = new HashMap<>();
        for (Operation operation : operations) described.put(operation, responsesOf(operation, schemas));
        for (Operation operation : operations) readingOf(operation, schemas, described.get(operation));

        ObjectNode document = NODES.objectNode().put("openapi", "3.1.0");
        document.putObject("info").put("title", "Ember API").put("version", "1.0");
        ObjectNode paths = document.putObject("paths");
        for (Operation operation : operations) {
            ObjectNode path = paths.has(operation.path())
                    ? (ObjectNode) paths.get(operation.path())
                    : paths.putObject(operation.path());
            path.set(operation.method().name().toLowerCase(Locale.ROOT), described.get(operation));
        }
        document.putObject("components").putObject("schemas").setAll(schemas.components());
        return WRITER.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n";
    }

    private static List<Operation> operations(Collection<Class<?>> routeClasses) {
        Map<String, Operation> byRoute = new TreeMap<>();
        List<String> duplicates = new ArrayList<>();
        for (Class<?> routes : routeClasses) {
            Stream.concat(Stream.of(routes.getDeclaredMethods()), Stream.of(routes.getDeclaredFields()))
                    .forEach(handler -> {
                        for (OpenApi annotation : annotationsOf(handler)) {
                            if (annotation.ignore()) continue;
                            for (HttpMethod method : annotation.methods()) {
                                var operation = new Operation(
                                        annotation.path(), method, annotation, locationOf(routes, handler));
                                Operation known = byRoute.putIfAbsent(operation.key(), operation);
                                if (known != null) {
                                    duplicates.add(method + " " + operation.path() + " in " + known.location() + " and "
                                            + operation.location());
                                }
                            }
                        }
                    });
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("Operations described twice:\n  " + String.join("\n  ", duplicates));
        }
        return List.copyOf(byRoute.values());
    }

    private static List<OpenApi> annotationsOf(AccessibleObject handler) {
        List<OpenApi> annotations = new ArrayList<>(List.of(handler.getAnnotationsByType(OpenApi.class)));
        OpenApis container = handler.getAnnotation(OpenApis.class);
        if (container != null && annotations.isEmpty()) annotations.addAll(List.of(container.value()));
        return annotations;
    }

    private static String locationOf(Class<?> routes, AccessibleObject handler) {
        return routes.getSimpleName() + "." + ((Member) handler).getName();
    }

    private static ObjectNode responsesOf(Operation operation, WireSchemas schemas) {
        ObjectNode described = NODES.objectNode();
        OpenApi annotation = operation.annotation();
        if (isSet(annotation.summary())) described.put("summary", annotation.summary());
        if (annotation.tags().length > 0) {
            ArrayNode tags = described.putArray("tags");
            for (String tag : annotation.tags()) tags.add(tag);
        }
        ObjectNode responses = described.putObject("responses");
        List<OpenApiResponse> ordered = Stream.of(annotation.responses())
                .sorted(Comparator.comparing(OpenApiResponse::status))
                .toList();
        for (OpenApiResponse response : ordered) {
            ObjectNode entry = responses.putObject(response.status());
            entry.put(
                    "description",
                    isSet(response.description()) ? response.description() : reasonOf(response.status()));
            if (response.content().length > 0)
                entry.set("content", contentOf(response.content(), schemas, Direction.RESPONSE));
        }
        return described;
    }

    private static void readingOf(Operation operation, WireSchemas schemas, ObjectNode described) {
        OpenApi annotation = operation.annotation();
        ArrayNode parameters = NODES.arrayNode();
        for (OpenApiParam param : annotation.pathParams()) parameters.add(parameterOf(param, "path", true, schemas));
        for (OpenApiParam param : annotation.queryParams())
            parameters.add(parameterOf(param, "query", param.required(), schemas));
        if (!parameters.isEmpty()) described.set("parameters", parameters);
        var body = annotation.requestBody();
        if (body.content().length > 0) {
            ObjectNode requestBody = described.putObject("requestBody");
            if (body.required()) requestBody.put("required", true);
            requestBody.set("content", contentOf(body.content(), schemas, Direction.REQUEST));
        }
    }

    private static ObjectNode parameterOf(OpenApiParam param, String in, boolean required, WireSchemas schemas) {
        ObjectNode parameter = NODES.objectNode().put("name", param.name()).put("in", in);
        if (required) parameter.put("required", true);
        parameter.set("schema", schemas.schemaOf(param.type(), Direction.REQUEST));
        return parameter;
    }

    private static ObjectNode contentOf(OpenApiContent[] contents, WireSchemas schemas, Direction direction) {
        ObjectNode content = NODES.objectNode();
        for (OpenApiContent entry : contents) {
            if (entry.from() == NULL_CLASS.class) {
                content.putObject(entry.type()).putObject("schema").put("type", "string");
                continue;
            }
            String mimeType =
                    ContentType.AUTODETECT.equals(entry.mimeType()) ? detectedMimeType(entry.from()) : entry.mimeType();
            content.putObject(mimeType).set("schema", schemas.schemaOf(entry.from(), direction));
        }
        return content;
    }

    private static String detectedMimeType(Class<?> from) {
        if (from == String.class) return "text/plain";
        if (from == byte[].class) return "application/octet-stream";
        return ContentType.JSON;
    }

    private static String reasonOf(String status) {
        try {
            return HttpStatus.forStatus(Integer.parseInt(status)).getMessage();
        } catch (NumberFormatException e) {
            return status;
        }
    }

    private static boolean isSet(String value) {
        return !OpenApiAnnotationsKt.NULL_STRING.equals(value);
    }

    /**
     * One method on one path, as one annotation describes it.
     *
     * @param path       the path, with its parameters in braces
     * @param method     the verb
     * @param annotation the annotation describing it
     * @param location   the route class and handler carrying the annotation
     */
    record Operation(String path, HttpMethod method, OpenApi annotation, String location) {
        String key() {
            return path + " " + method.ordinal() + " " + method;
        }
    }
}
