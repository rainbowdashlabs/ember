/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    AccountPasswordState, InstanceUserType, type AccountOverview, type AssociationRole,
} from '@/api/generated/schema'
import {ColumnTypes, enumOptions, type ColumnOption, type TableColumn} from '@/components/table/tableColumn'

/** The password states in the order the list sorts and offers them. */
const PASSWORD_STATES: readonly AccountPasswordState[] = [
    AccountPasswordState.NONE, AccountPasswordState.ON, AccountPasswordState.OFF, AccountPasswordState.ONE_TIME,
]

/** Whether the account holds a password, whether it works, and whether it is a one-time password. */
export function passwordStateOf(account: AccountOverview): AccountPasswordState {
    if (!account.hasPassword) return AccountPasswordState.NONE
    if (!account.passwordSignIn) return AccountPasswordState.OFF
    return account.oneTimePasswordExpiresAt ? AccountPasswordState.ONE_TIME : AccountPasswordState.ON
}

/** One association role as the list writes it: the association and what the account is there. */
export function associationLabel(role: AssociationRole, t: (key: string) => string): string {
    return `${role.association} (${t(`clusterOverview.role.${role.role}`)})`
}

/** The stations and associations an account can be filtered by, each by its uid and its name. */
export interface AccountPlaces {
    stations: readonly ColumnOption[]
    associations: readonly ColumnOption[]
}

const NO_PLACES: AccountPlaces = {stations: [], associations: []}

/**
 * The columns of the instance's account list: who, how they sign in by name, where they belong,
 * what they are on the instance, when they last signed in, what they sign in with and whether their
 * address is confirmed.
 *
 * <p>The server filters and sorts the list, so a column is filterable and sortable only where the
 * server is asked about it. The name and the sign-in name are found through the search instead.
 * Stations and associations are filtered by the places given, which are what their filters offer.
 * Only the columns whose empty cells the server knows how to ask for offer them in their filter.
 */
export function accountColumns(t: (key: string) => string, places: AccountPlaces = NO_PLACES): TableColumn<AccountOverview>[] {
    return [
        {
            key: 'name', label: t('adminAccounts.col.name'), type: ColumnTypes.TEXT, value: account => account.name,
            pinned: true, filterable: false,
        },
        {
            key: 'loginName', label: t('adminAccounts.col.loginName'), type: ColumnTypes.TEXT,
            value: account => account.loginName, sortable: false, filterable: false,
        },
        {
            key: 'stations', label: t('adminAccounts.col.stations'), type: ColumnTypes.TEXT,
            value: account => account.stations, options: places.stations, sortable: false, filtersEmpty: true,
        },
        {
            key: 'associations', label: t('adminAccounts.col.associations'), type: ColumnTypes.TEXT,
            value: account => account.associations.map(role => associationLabel(role, t)),
            options: places.associations, sortable: false, filtersEmpty: true,
        },
        {
            key: 'role', label: t('adminAccounts.col.role'), type: ColumnTypes.ENUM, value: account => account.instanceUserType,
            options: enumOptions(Object.values(InstanceUserType), value => t(`adminAccounts.roles.${value}`)), sortable: false,
        },
        {
            key: 'lastSignIn', label: t('adminAccounts.col.lastSignIn'), type: ColumnTypes.DATE_TIME,
            value: account => account.lastSignInAt, filtersEmpty: true,
        },
        {
            key: 'password', label: t('adminAccounts.col.password'), type: ColumnTypes.ENUM, value: passwordStateOf,
            options: enumOptions(PASSWORD_STATES, value => t(`adminAccounts.password.${value}`)), sortable: false,
        },
        {key: 'passkeys', label: t('adminAccounts.col.passkeys'), type: ColumnTypes.NUMBER, value: account => account.passkeys},
        {
            key: 'twoFactor', label: t('adminAccounts.col.twoFactor'), type: ColumnTypes.BOOLEAN,
            value: account => account.twoFactor, sortable: false,
        },
        {
            key: 'confirmed', label: t('adminAccounts.col.confirmed'), type: ColumnTypes.BOOLEAN,
            value: account => account.confirmed, sortable: false, filtersEmpty: true,
        },
    ]
}
