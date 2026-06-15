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

    enum CalloutVariant {
        INFO,
        WARNING,
        SUCCESS,
        TIP
    }

    record MarkdownConfig() implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ImageConfig(ImageFit imageFit, String altText, Integer maxHeight, String description)
            implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record VideoConfig(Boolean autoplay, Boolean loop) implements CellConfig {}

    /** Callout box. The body text lives in cell.content (markdown). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CalloutConfig(CalloutVariant variant, String title) implements CellConfig {}

    /** Quote block. The quote text lives in cell.content. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QuoteConfig(String author, String attributionUrl) implements CellConfig {}

    /** Horizontal divider with optional centred label. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DividerConfig(String label) implements CellConfig {}

    /** Vertical spacer. Height in CSS pixels. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SpacerConfig(Integer heightPx) implements CellConfig {}

    /** Collapsible accordion. The body markdown lives in cell.content. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AccordionConfig(String title, Boolean openByDefault) implements CellConfig {}

    /** Embedded PDF viewer. url is required; height is in CSS pixels. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PdfConfig(String url, Integer heightPx) implements CellConfig {}

    /** Download card pointing at any file URL. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FileDownloadConfig(String url, String label, String description) implements CellConfig {}

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
