/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test as base, type APIRequestContext, type Browser, type Page} from '@playwright/test'

/**
 * A logged-in page per role, each in its own browser context.
 *
 * Separate contexts rather than separate tests: the permission stories need a manager and a member
 * live at the same time — grant on one, observe on the other — and a shared context would share the
 * session token, which is exactly what those stories must not do.
 *
 * The session is obtained through the demo login endpoint rather than by clicking through the
 * picker. One story walks that UI on purpose (ACC-1); every other story only needs to *be* someone,
 * and paying for the picker in each of them would buy a slower suite and a hundred ways to break on
 * a login page change.
 */
export interface DemoAccount {
    email: string
    firstName: string
    lastName: string
    userType: string
    permissions: string[]
    /** The station the account belongs to, carried over from the group it was listed under. */
    stationId?: string
}

/**
 * The demo accounts the instance offers, flattened out of whichever shape the endpoint answers
 * with. Discovered rather than hardcoded so the fixtures follow the seeder instead of duplicating
 * its choice of names.
 */
export async function demoAccounts(request: APIRequestContext): Promise<DemoAccount[]> {
    const response = await request.get('/api/v1/demo/accounts')
    if (!response.ok()) {
        throw new Error(
            `The demo accounts endpoint answered ${response.status()}. The backend has to run with `
            + 'demo.dev or demo.enabled for the end-to-end suite.',
        )
    }
    const payload = await response.json()
    const groups: {stationId?: string; accounts?: DemoAccount[]}[] = Array.isArray(payload)
        ? payload.map(entry => ('accounts' in entry ? entry : {accounts: [entry as DemoAccount]}))
        : [{accounts: payload.noStationAccounts ?? []}, ...(payload.stationGroups ?? [])]

    return groups.flatMap(group =>
        (group.accounts ?? []).map(account => ({...account, stationId: group.stationId})))
}

/**
 * The first demo account holding every one of the given permissions, so a story asks for the rights
 * it needs rather than for a name it hopes still exists.
 */
export async function accountWith(request: APIRequestContext, ...permissions: string[]): Promise<DemoAccount> {
    const accounts = await demoAccounts(request)
    const match = accounts.find(account => permissions.every(permission => account.permissions.includes(permission)))
    if (!match) throw new Error(`No demo account holds ${permissions.join(', ')}`)
    return match
}

/** The first demo account of the given user type that holds none of the given permissions. */
export async function accountWithout(
    request: APIRequestContext,
    userType: string,
    ...permissions: string[]
): Promise<DemoAccount> {
    const accounts = await demoAccounts(request)
    const match = accounts.find(account =>
        account.userType === userType && permissions.every(permission => !account.permissions.includes(permission)))
    if (!match) throw new Error(`No ${userType} demo account is free of ${permissions.join(', ')}`)
    return match
}

/**
 * Opens a page already logged in as the given account.
 *
 * Both the token and the chosen station are planted, which is what the application itself stores
 * after a login: a session alone leaves the station area redirecting to the station picker, so a
 * fixture that plants only the token lands every story on the wrong page.
 */
export async function pageAs(browser: Browser, request: APIRequestContext, account: DemoAccount): Promise<Page> {
    const login = await request.post('/api/v1/demo/login', {data: {email: account.email}})
    if (!login.ok()) throw new Error(`Demo login for ${account.email} answered ${login.status()}`)
    const {token} = await login.json()

    const context = await browser.newContext()
    await context.addInitScript(([sessionToken, stationId]) => {
        window.localStorage.setItem('session_token', sessionToken)
        if (stationId) window.localStorage.setItem('station_id', stationId)
        window.localStorage.setItem('storage_consent', 'accepted')
    }, [token, account.stationId ?? ''])
    return context.newPage()
}

interface Fixtures {
    managerPage: Page
    memberPage: Page
}

export const test = base.extend<Fixtures>({
    managerPage: async ({browser, request}, use) => {
        const account = await accountWith(request, 'STATION_MANAGER')
        const page = await pageAs(browser, request, account)
        await use(page)
        await page.context().close()
    },

    memberPage: async ({browser, request}, use) => {
        const account = await accountWithout(request, 'MEMBER', 'STATION_MANAGER', 'MEMBER_EDIT')
        const page = await pageAs(browser, request, account)
        await use(page)
        await page.context().close()
    },
})

export {expect} from '@playwright/test'
