/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ApplicationStatus, type StationApplication} from '@/api/stationApplications'
import {ColumnTypes, enumOptions, type TableColumn} from '@/components/table/tableColumn'

/** The words each state of an application reads as, in the order they sort in. */
const STATUS_LABEL_KEYS: Record<string, string> = {
    [ApplicationStatus.UNVERIFIED]: 'adminApplications.unverified',
    [ApplicationStatus.PENDING]: 'adminApplications.pendingBadge',
    [ApplicationStatus.ACCEPTED]: 'adminApplications.accepted',
    [ApplicationStatus.DENIED]: 'adminApplications.denied',
}

/** The columns of the station applications: who applied, for which station, when, and how it stands. */
export function applicationColumns(t: (key: string) => string): TableColumn<StationApplication>[] {
    return [
        {key: 'name', label: t('adminApplications.name'), type: ColumnTypes.TEXT, value: app => `${app.firstName} ${app.lastName}`, pinned: true},
        {key: 'email', label: t('adminApplications.email'), type: ColumnTypes.TEXT, value: app => app.email},
        {key: 'station', label: t('adminApplications.station'), type: ColumnTypes.TEXT, value: app => app.stationName},
        {key: 'createdAt', label: t('adminApplications.date'), type: ColumnTypes.DATE_TIME, value: app => app.createdAt},
        {
            key: 'status', label: t('adminApplications.status'), type: ColumnTypes.ENUM, value: app => app.status,
            options: enumOptions(Object.keys(STATUS_LABEL_KEYS), value => t(STATUS_LABEL_KEYS[value]!)),
        },
    ]
}
