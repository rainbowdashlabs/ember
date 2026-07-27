/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import tools.jackson.databind.ObjectMapper;

/**
 * Shared default-configuration JSON mapper for internal (de)serialization — storage
 * backend metadata, entity JSONB payloads, CSV/AI processing, federation version
 * hashing. Deliberately distinct from the API-boundary mapper in the HTTP server,
 * which carries the station-id translation module and strict payload settings that
 * must not leak into internal persistence formats.
 */
public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {}
}
