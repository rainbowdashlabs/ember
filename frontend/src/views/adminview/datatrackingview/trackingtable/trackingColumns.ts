/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {TrackingStatus, type TableEntry, type TrackingStatusName} from '@/api/dataTracking'
import {ColumnTypes, enumOptions, type TableColumn} from '@/components/table/tableColumn'
import type {TrackingRow} from './trackingRow'

interface TrackingContext {
    labelKey: string
    statusOf: (entry: TableEntry) => TrackingStatusName
}

/** The three contexts a table is tracked in, by the key of the column that shows how it stands there. */
export const TRACKING_CONTEXTS: Record<string, TrackingContext> = {
    transfer: {labelKey: 'adminDataTracking.stationTransfer', statusOf: entry => entry.stationTransfer.status},
    gdprExport: {labelKey: 'adminDataTracking.gdprExport', statusOf: entry => entry.gdprExport.status},
    gdprDeletion: {labelKey: 'adminDataTracking.gdprDeletion', statusOf: entry => entry.gdprDeletion.status},
}

function strategiesOf(row: TrackingRow): string[] {
    return [...new Set((row.entry.gdprDeletion?.strategies ?? []).map(strategy => strategy.strategy).filter(Boolean))]
}

/**
 * The columns of the tracking inventory: the table, how each of its three contexts stands, the
 * deletion strategies it carries, and how many of its columns nobody has verified yet.
 */
export function trackingColumns(t: (key: string) => string): TableColumn<TrackingRow>[] {
    const statuses = enumOptions(Object.values(TrackingStatus), value => t(`adminDataTracking.filter.${value}`))
    const contexts = Object.entries(TRACKING_CONTEXTS).map(([key, context]): TableColumn<TrackingRow> => ({
        key, label: t(context.labelKey), type: ColumnTypes.ENUM, value: row => context.statusOf(row.entry), options: statuses,
    }))
    return [
        {key: 'name', label: t('adminDataTracking.colName'), type: ColumnTypes.TEXT, value: row => row.name, pinned: true},
        ...contexts,
        {key: 'strategies', label: t('adminDataTracking.detail.strategies'), type: ColumnTypes.TEXT, value: strategiesOf},
        {
            key: 'unverifiedColumns', label: t('adminDataTracking.unverifiedColumns'), type: ColumnTypes.NUMBER,
            value: row => row.unverifiedColumns || null, align: 'right',
        },
    ]
}
