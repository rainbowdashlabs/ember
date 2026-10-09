/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {InstanceMailStation} from '@/api/generated/schema'
import {ColumnTypes, type TableColumn} from '@/components/table/tableColumn'

/**
 * The columns of the stations an administrator lets send through the instance's providers: who, whether
 * they may, how much a day, and what went out for them today.
 */
export function stationGrantColumns(t: (key: string) => string): TableColumn<InstanceMailStation>[] {
    return [
        {key: 'name', label: t('instanceMail.stations.colName'), type: ColumnTypes.TEXT, value: station => station.name, pinned: true},
        {
            key: 'granted', label: t('instanceMail.stations.colGranted'), type: ColumnTypes.BOOLEAN, value: station => station.granted,
            booleanLabels: {yes: t('instanceMail.stations.granted'), no: t('instanceMail.stations.notGranted')},
        },
        {key: 'dailyLimit', label: t('instanceMail.stations.colDailyLimit'), type: ColumnTypes.NUMBER, value: station => station.dailyLimit},
        {key: 'sentToday', label: t('instanceMail.stations.colSentToday'), type: ColumnTypes.NUMBER, value: station => station.sentToday},
    ]
}
