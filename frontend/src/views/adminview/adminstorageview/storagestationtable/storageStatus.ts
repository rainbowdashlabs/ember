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

/** How full the station's room is, or null where it keeps its files on a backend of its own. */
export function storageStatusOf(station: StorageRoomRow): StorageStatusName | null {
    if (station.usesOwnBackend) return null
    if (station.quotaUsedPercent >= 95) return StorageStatus.FULL
    if (station.quotaUsedPercent >= 80) return StorageStatus.WARNING
    return StorageStatus.OK
}
