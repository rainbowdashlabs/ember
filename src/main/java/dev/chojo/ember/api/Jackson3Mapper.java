/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.json.JsonMapper;
import io.javalin.json.PipedStreamExecutor;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SequenceWriter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Javalin {@link JsonMapper} implementation backed by Jackson 3.
 * Handles serialization and deserialization of JSON for all API request and response bodies.
 */
@SuppressWarnings("NullableProblems")
public class Jackson3Mapper implements JsonMapper {
    private final ObjectMapper mapper;

    /**
     * Creates a mapper using the given Jackson {@link ObjectMapper} with blocking threads.
     *
     * @param mapper the Jackson object mapper to use
     */
    public Jackson3Mapper(ObjectMapper mapper) {
        this(mapper, false);
    }

    /**
     * Creates a mapper using the given Jackson {@link ObjectMapper}.
     *
     * @param mapper            the Jackson object mapper to use
     * @param useVirtualThreads whether to use virtual threads for piped stream execution
     */
    public Jackson3Mapper(ObjectMapper mapper, boolean useVirtualThreads) {
        this.mapper = mapper;
        PipedStreamExecutor executor = new PipedStreamExecutor(useVirtualThreads);
    }

    @Override
    public <T> @NonNull T fromJsonString(String json, Type targetType) {
        return mapper.readValue(json, mapper.getTypeFactory().constructType(targetType));
    }

    @Override
    public <T> @NonNull T fromJsonStream(InputStream json, Type targetType) {
        return mapper.readValue(json, mapper.getTypeFactory().constructType(targetType));
    }

    @Override
    public String toJsonString(Object obj, Type type) {
        return switch (obj) {
            case String s -> s;
            case Object o -> mapper.writeValueAsString(o);
        };
    }

    @Override
    public InputStream toJsonStream(Object obj, Type type) {
        if (Objects.requireNonNull(obj) instanceof String s) {
            return new ByteArrayInputStream(s.getBytes());
        }
        try {
            return new ByteArrayInputStream(mapper.writeValueAsBytes(obj));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeToOutputStream(Stream<?> stream, OutputStream outputStream) {
        try (SequenceWriter sequenceWriter = mapper.writer().writeValuesAsArray(outputStream)) {
            stream.forEach(sequenceWriter::write);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
