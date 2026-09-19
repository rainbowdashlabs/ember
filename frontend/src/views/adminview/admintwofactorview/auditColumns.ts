/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {TwoFactorEvent, TwoFactorKind, type AuditEntry} from '@/api/twoFactorAdmin'
import {ColumnTypes, type ColumnOption, type TableColumn} from '@/components/table/tableColumn'
import {formatDateTime} from '@/util/format'

function eventOptions(t: (key: string) => string): ColumnOption[] {
    return Object.values(TwoFactorEvent).map(value => ({value, label: t(`twoFactor.admin.audit.events.${value}`)}))
}

function factorOptions(t: (key: string) => string): ColumnOption[] {
    return Object.values(TwoFactorKind).map(value => ({value, label: t(`twoFactor.admin.audit.factors.${value}`)}))
}

/** The columns of the two-factor audit log: when, whose account, who did it, what, with which factor, from where. */
export function auditColumns(t: (key: string) => string): TableColumn<AuditEntry>[] {
    return [
        {
            key: 'when', label: t('twoFactor.admin.audit.col.when'), type: ColumnTypes.DATE,
            value: entry => entry.createdAt, display: entry => formatDateTime(entry.createdAt),
        },
        {key: 'account', label: t('twoFactor.admin.audit.col.account'), type: ColumnTypes.NUMBER, value: entry => entry.accountId},
        {key: 'actor', label: t('twoFactor.admin.audit.col.actor'), type: ColumnTypes.NUMBER, value: entry => entry.actorId},
        {
            key: 'event', label: t('twoFactor.admin.audit.col.event'), type: ColumnTypes.ENUM,
            value: entry => entry.event, options: eventOptions(t),
        },
        {
            key: 'factor', label: t('twoFactor.admin.audit.col.factor'), type: ColumnTypes.ENUM,
            value: entry => entry.factorKind, options: factorOptions(t),
        },
        {key: 'country', label: t('twoFactor.admin.audit.col.country'), type: ColumnTypes.TEXT, value: entry => entry.country},
    ]
}
