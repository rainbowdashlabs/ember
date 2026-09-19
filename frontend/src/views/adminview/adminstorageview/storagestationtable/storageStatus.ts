/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {StorageRoomRow} from '@/composables/useStorageQuotas'

/** How full a station's room is, in the order the states sort in. */
export const StorageStatus = {
    OK: 'ok',
    WARNING: 'warning',
    FULL: 'full',
} as const

export type StorageStatusName = (typeof StorageStatus)[keyof typeof StorageStatus]

/** The share of a quota, in percent, from which a room counts as full. */
const STORAGE_FULL_PERCENT = 95

/** The share of a quota, in percent, from which a room counts as filling up. */
const STORAGE_WARNING_PERCENT = 80

/** How full a room is that uses the given share of its quota, in percent. */
export function storageStatusOfPercent(percent: number): StorageStatusName {
    if (percent >= STORAGE_FULL_PERCENT) return StorageStatus.FULL
    if (percent >= STORAGE_WARNING_PERCENT) return StorageStatus.WARNING
    return StorageStatus.OK
}

/** How full the station's room is, or null where it keeps its files on a backend of its own. */
export function storageStatusOf(station: StorageRoomRow): StorageStatusName | null {
    if (station.usesOwnBackend) return null
    return storageStatusOfPercent(station.quotaUsedPercent)
}
