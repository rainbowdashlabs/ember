/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The properties of an appointment a field of its attendance sheet can be filled in from.
 *
 * <p>The names are what the server stores and understands. What each one is called is in the
 * translations under {@code events.defaultSources}, which is what kept three screens from each
 * spelling them out in German of their own.
 */
export const DEFAULT_SOURCES = ['EVENT_NAME', 'EVENT_DESCRIPTION', 'EVENT_START_TIME', 'EVENT_END_TIME'] as const

export type DefaultSourceName = (typeof DEFAULT_SOURCES)[number]
