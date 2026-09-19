/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {TwoFactorEvent, TwoFactorKind, type AuditEntry} from '@/api/twoFactorAdmin'
import {ColumnTypes, enumOptions, type TableColumn} from '@/components/table/tableColumn'

/** The columns of the two-factor audit log: when, whose account, who did it, what, with which factor, from where. */
export function auditColumns(t: (key: string) => string): TableColumn<AuditEntry>[] {
    return [
        {key: 'when', label: t('twoFactor.admin.audit.col.when'), type: ColumnTypes.DATE_TIME, value: entry => entry.createdAt},
        {key: 'account', label: t('twoFactor.admin.audit.col.account'), type: ColumnTypes.NUMBER, value: entry => entry.accountId},
        {key: 'actor', label: t('twoFactor.admin.audit.col.actor'), type: ColumnTypes.NUMBER, value: entry => entry.actorId},
        {
            key: 'event', label: t('twoFactor.admin.audit.col.event'), type: ColumnTypes.ENUM, value: entry => entry.event,
            options: enumOptions(Object.values(TwoFactorEvent), value => t(`twoFactor.admin.audit.events.${value}`)),
        },
        {
            key: 'factor', label: t('twoFactor.admin.audit.col.factor'), type: ColumnTypes.ENUM, value: entry => entry.factorKind,
            options: enumOptions(Object.values(TwoFactorKind), value => t(`twoFactor.admin.audit.factors.${value}`)),
        },
        {key: 'country', label: t('twoFactor.admin.audit.col.country'), type: ColumnTypes.TEXT, value: entry => entry.country},
    ]
}
