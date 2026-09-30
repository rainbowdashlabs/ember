/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */

/**
 * A copy of a record without one of its entries, leaving the record itself as it was.
 *
 * @param record the record to copy
 * @param key the entry to leave out
 * @returns a new record carrying every other entry
 */
export function withoutKey<K extends PropertyKey, V>(record: Record<K, V>, key: NoInfer<K>): Record<K, V> {
    return Object.fromEntries(Object.entries(record).filter(([entry]) => entry !== String(key))) as Record<K, V>
}
