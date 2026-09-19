/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {BeaconMetricsRow} from '@/api/beacon'
import {ColumnTypes, type TableColumn} from '@/components/table/tableColumn'

/**
 * Where a bucket starts, so buckets sort by size rather than by their spelling. A bucket below a
 * bound ("<10") sorts just under it.
 */
export function bucketLowerBound(bucket: string | null): number | null {
    if (!bucket) return null
    const bound = Number.parseInt(bucket.replace(/^</, ''), 10)
    if (Number.isNaN(bound)) return null
    return bucket.startsWith('<') ? bound - 0.5 : bound
}

function bucketColumn(
    key: string,
    label: string,
    bucket: (row: BeaconMetricsRow) => string | null,
    defaultVisible = true,
): TableColumn<BeaconMetricsRow> {
    return {key, label, type: ColumnTypes.TEXT, value: bucket, sortValue: row => bucketLowerBound(bucket(row)), defaultVisible}
}

/** The columns of the reported figures: the day, what reported, and the bucket each count fell in. */
export function beaconMetricColumns(t: (key: string) => string): TableColumn<BeaconMetricsRow>[] {
    return [
        {key: 'day', label: t('beacon.day'), type: ColumnTypes.DATE, value: row => row.day, pinned: true},
        {key: 'subject', label: t('beacon.subject'), type: ColumnTypes.TEXT, value: row => row.subject},
        bucketColumn('members', t('sidebar.members'), row => row.members),
        bucketColumn('inventory', t('sidebar.inventory'), row => row.inventory),
        bucketColumn('accounts', t('beacon.accounts'), row => row.accounts, false),
        bucketColumn('stations', t('beacon.stations'), row => row.stations, false),
    ]
}
