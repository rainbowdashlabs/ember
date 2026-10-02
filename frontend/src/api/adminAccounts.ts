/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {AccountOverviewPage, components, IssuedOneTimePassword} from './generated/schema'

export type InstanceUserTypeName = components['schemas']['InstanceUserType']

/** What an account is on the instance. */
export const InstanceUserType = {
    USER: 'USER',
    ADMINISTRATOR: 'ADMINISTRATOR',
} as const satisfies Record<InstanceUserTypeName, InstanceUserTypeName>

/** What the account list is asked for: a search and a page of it. */
export interface AccountListQuery {
    q?: string
    page: number
    size: number
}

/** One page of every account of the instance, searched and paged on the server. */
export async function listAccounts(query: AccountListQuery): Promise<AccountOverviewPage> {
    const params: Record<string, string | number> = {page: query.page, size: query.size}
    if (query.q) params.q = query.q
    const res = await client.get<AccountOverviewPage>('/admin/accounts', {params})
    return res.data
}

/**
 * A one-time password for any account but one's own. It replaces the account's password, ends its
 * sessions and is answered only this once.
 */
export async function issueOneTimePassword(accountId: number): Promise<IssuedOneTimePassword> {
    const res = await client.post<IssuedOneTimePassword>(`/admin/accounts/${accountId}/one-time-password`)
    return res.data
}
