/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {AccountOverview} from '@/api/generated/schema'
import type {Sheet} from '@/util/printSheet'

/** The account the article opens: a new member waiting to replace their one-time password. */
export const SAMPLE_OPENED_ACCOUNT: AccountOverview = {
    id: 1, uid: '00000000-0000-0000-0000-000000000001', name: 'Lena Weber',
    email: 'lena.weber@example.org', loginName: 'lena.weber@example.org', instanceUserType: 'USER',
    lastSignInAt: null, stations: ['Feuerwache Nord'], associations: [],
    hasPassword: true, passwordSignIn: true, oneTimePasswordExpiresAt: '2026-10-09T18:00:00Z',
    passkeys: 0, twoFactor: false,
}

/** Three accounts the help article shows: one with a one-time password, one with passkeys, one administrator. */
export const SAMPLE_ACCOUNTS: readonly AccountOverview[] = [
    SAMPLE_OPENED_ACCOUNT,
    {
        id: 2, uid: '00000000-0000-0000-0000-000000000002', name: 'Jonas Becker',
        email: 'jonas.becker@example.org', loginName: 'jbecker', instanceUserType: 'USER',
        lastSignInAt: '2026-09-28T07:45:00Z', stations: ['Feuerwache Nord', 'Feuerwache Süd'],
        associations: [{association: 'Kreisfeuerwehrverband', role: 'CLUSTER_ADMIN'}],
        hasPassword: true, passwordSignIn: false, oneTimePasswordExpiresAt: null, passkeys: 2, twoFactor: true,
    },
    {
        id: 3, uid: '00000000-0000-0000-0000-000000000003', name: 'Miriam Schulz',
        email: 'miriam.schulz@example.org', loginName: 'miriam.schulz@example.org', instanceUserType: 'ADMINISTRATOR',
        lastSignInAt: '2026-10-01T19:12:00Z', stations: [], associations: [],
        hasPassword: true, passwordSignIn: true, oneTimePasswordExpiresAt: null, passkeys: 1, twoFactor: true,
    },
]

/** The access details of the opened sample account, as the one-time password dialog shows them. */
export function sampleSheet(t: (key: string, values?: Record<string, unknown>) => string): Sheet {
    return {
        title: t('oneTimePassword.dialog.title', {name: SAMPLE_OPENED_ACCOUNT.name}),
        lines: [
            {label: t('oneTimePassword.dialog.loginName'), value: SAMPLE_OPENED_ACCOUNT.loginName},
            {label: t('oneTimePassword.dialog.password'), value: 'k7mq-x2pd-9wtr-hb4z'},
            {label: t('oneTimePassword.dialog.instance'), value: 'https://ember.example.org'},
            {label: t('oneTimePassword.dialog.expires'), value: '09.10.2026, 20:00'},
        ],
        note: t('oneTimePassword.dialog.note'),
    }
}
