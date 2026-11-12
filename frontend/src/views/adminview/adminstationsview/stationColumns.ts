/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {AdminStationSummary} from '@/api/generated/schema'
import {ColumnTypes, enumOptions, type TableColumn} from '@/components/table/tableColumn'
import {STATE_LABEL_KEYS, STATE_ORDER, waitsForManager} from './stationList'

/** The columns of the station table: the same facts the tiles show, plus when each station was made. */
export function stationColumns(t: (key: string) => string): TableColumn<AdminStationSummary>[] {
    return [
        {key: 'name', label: t('adminStations.name'), type: ColumnTypes.TEXT, value: station => station.name, pinned: true},
        {
            key: 'state', label: t('adminStations.state'), type: ColumnTypes.ENUM, value: station => station.state,
            options: enumOptions(STATE_ORDER, state => t(STATE_LABEL_KEYS[state as keyof typeof STATE_LABEL_KEYS])),
        },
        {key: 'memberCount', label: t('adminStations.members'), type: ColumnTypes.NUMBER, value: station => station.memberCount},
        {
            key: 'manager', label: t('adminStations.manager'), type: ColumnTypes.TEXT,
            value: station => station.managerName ?? station.managerEmail,
        },
        {key: 'association', label: t('adminStations.association'), type: ColumnTypes.TEXT, value: station => station.associationName},
        {
            key: 'waitingSince', label: t('adminStations.waitingSince'), type: ColumnTypes.DATE_TIME,
            value: station => waitsForManager(station) ? station.waitingSince : null,
        },
        {
            key: 'lastReminder', label: t('adminStations.lastReminder'), type: ColumnTypes.DATE_TIME,
            value: station => station.lastReminder?.sentAt,
        },
        {
            key: 'createdAt', label: t('adminStations.createdAt'), type: ColumnTypes.DATE_TIME,
            value: station => station.createdAt, defaultVisible: false,
        },
    ]
}
