/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {AccountOverviewPage, AccountSetupMail, IssuedOneTimePassword} from './generated/schema'

/**
 * The filters and the order the account list asks for, as query parameters: each named once, or
 * several times where its values are alternatives.
 */
export type AccountFilterParams = Record<string, string | string[]>

/** What the account list is asked for: a search, its filters and order, and a page of it. */
export interface AccountListQuery {
    q?: string
    page: number
    size: number
    filters?: AccountFilterParams
}

/** The query parameters of a list request, with every value of a repeated filter named once. */
export function accountSearchParams(query: AccountListQuery): URLSearchParams {
    const params = new URLSearchParams({page: String(query.page), size: String(query.size)})
    if (query.q) params.set('q', query.q)
    for (const [name, value] of Object.entries(query.filters ?? {})) {
        for (const single of typeof value === 'string' ? [value] : value) params.append(name, single)
    }
    return params
}

/** One page of every account of the instance, searched, filtered, sorted and paged on the server. */
export async function listAccounts(query: AccountListQuery): Promise<AccountOverviewPage> {
    const res = await client.get<AccountOverviewPage>('/admin/accounts', {params: accountSearchParams(query)})
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

/**
 * Sends an account that is not yet ready the mail it waits for: the invitation for one without a
 * password of its own, the confirmation for one with an unconfirmed address.
 */
export async function sendSetupMail(accountId: number): Promise<AccountSetupMail> {
    const res = await client.post<AccountSetupMail>(`/admin/accounts/${accountId}/setup-mail`)
    return res.data
}
