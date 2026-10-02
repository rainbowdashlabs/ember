/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {InstanceUserType, type AccountOverview, type AssociationRole} from '@/api/generated/schema'
import {ColumnTypes, enumOptions, type TableColumn} from '@/components/table/tableColumn'

/** How an account's password stands, as the list tells them apart. */
export const PasswordState = {
    NONE: 'NONE',
    ON: 'ON',
    OFF: 'OFF',
    ONE_TIME: 'ONE_TIME',
} as const

export type PasswordStateName = (typeof PasswordState)[keyof typeof PasswordState]

/** Whether the account holds a password, whether it works, and whether it is a one-time password. */
export function passwordStateOf(account: AccountOverview): PasswordStateName {
    if (!account.hasPassword) return PasswordState.NONE
    if (!account.passwordSignIn) return PasswordState.OFF
    return account.oneTimePasswordExpiresAt ? PasswordState.ONE_TIME : PasswordState.ON
}

/** One association role as the list writes it: the association and what the account is there. */
export function associationLabel(role: AssociationRole, t: (key: string) => string): string {
    return `${role.association} (${t(`clusterOverview.role.${role.role}`)})`
}

/**
 * The columns of the instance's account list: who, how they sign in by name, where they belong,
 * what they are on the instance, when they last signed in and what they sign in with.
 */
export function accountColumns(t: (key: string) => string): TableColumn<AccountOverview>[] {
    return [
        {key: 'name', label: t('adminAccounts.col.name'), type: ColumnTypes.TEXT, value: account => account.name, pinned: true},
        {key: 'loginName', label: t('adminAccounts.col.loginName'), type: ColumnTypes.TEXT, value: account => account.loginName},
        {key: 'stations', label: t('adminAccounts.col.stations'), type: ColumnTypes.TEXT, value: account => account.stations},
        {
            key: 'associations', label: t('adminAccounts.col.associations'), type: ColumnTypes.TEXT,
            value: account => account.associations.map(role => associationLabel(role, t)),
        },
        {
            key: 'role', label: t('adminAccounts.col.role'), type: ColumnTypes.ENUM, value: account => account.instanceUserType,
            options: enumOptions(Object.values(InstanceUserType), value => t(`adminAccounts.roles.${value}`)),
        },
        {key: 'lastSignIn', label: t('adminAccounts.col.lastSignIn'), type: ColumnTypes.DATE_TIME, value: account => account.lastSignInAt},
        {
            key: 'password', label: t('adminAccounts.col.password'), type: ColumnTypes.ENUM, value: passwordStateOf,
            options: enumOptions(Object.values(PasswordState), value => t(`adminAccounts.password.${value}`)),
        },
        {key: 'passkeys', label: t('adminAccounts.col.passkeys'), type: ColumnTypes.NUMBER, value: account => account.passkeys},
        {key: 'twoFactor', label: t('adminAccounts.col.twoFactor'), type: ColumnTypes.BOOLEAN, value: account => account.twoFactor},
    ]
}
