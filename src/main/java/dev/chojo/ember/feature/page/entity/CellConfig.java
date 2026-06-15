/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface CellConfig {
    Logger log = LoggerFactory.getLogger(CellConfig.class);
    ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .build();

    CellConfig EMPTY = new MarkdownConfig();

    enum ImageFit {
        COVER,
        CONTAIN,
        FILL
    }

    record MarkdownConfig() implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ImageConfig(ImageFit imageFit, String altText, Integer maxHeight, String description)
            implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record VideoConfig(Boolean autoplay, Boolean loop) implements CellConfig {}

    static CellConfig parse(CellContentType type, String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return type.emptyConfig();
        }
        try {
            return MAPPER.readValue(json, type.configClass());
        } catch (Exception e) {
            log.error("Failed to parse CellConfig for type {}: {}", type, json, e);
            return type.emptyConfig();
        }
    }

    default String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            log.error("Failed to serialize CellConfig", e);
            return "{}";
        }
    }
}
