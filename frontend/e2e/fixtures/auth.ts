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
 * live at the same time - grant on one, observe on the other - and a shared context would share the
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
    /** Whether the account administers the instance. Station permissions say nothing about that. */
    instanceAdministrator?: boolean
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
 * The first demo account holding any of the given permissions, so a story asks for the rights it
 * needs rather than for a name it hopes still exists.
 *
 * Any rather than all, because the endpoint reports what was granted directly and not what those
 * grants imply: the station administrator right carries every management right with it and appears
 * on its own.
 */
export async function accountWith(request: APIRequestContext, ...permissions: string[]): Promise<DemoAccount> {
    const accounts = await demoAccounts(request)
    const match = accounts.find(account => permissions.some(permission => account.permissions.includes(permission)))
    if (!match) throw new Error(`No demo account holds any of ${permissions.join(', ')}`)
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
 * A station that has both someone who runs it and an ordinary member, with both accounts.
 *
 * The two-actor stories are only meaningful inside one station: a manager granting a permission in
 * one station and a member watching from another proves nothing, and picking each role
 * independently is exactly how that happens - the seeder has several stations and not all of them
 * have members.
 *
 * Both must carry an address to log in with: a station holds members who never sign in themselves,
 * and one of those cannot be a role the suite acts as.
 */
export async function stationPeers(request: APIRequestContext): Promise<{manager: DemoAccount; member: DemoAccount}> {
    const accounts = await demoAccounts(request)
    const managers = accounts.filter(account => !!account.email
        && (account.permissions.includes('STATION_ADMINISTRATOR') || account.permissions.includes('STATION_MANAGER')))
    for (const manager of managers) {
        const member = accounts.find(account =>
            account.stationId === manager.stationId
            && account.userType === 'MEMBER'
            && !!account.email
            && !account.permissions.includes('STATION_MANAGER'))
        if (member && manager.stationId) return {manager, member}
    }
    throw new Error('No seeded station has both a manager and an ordinary member')
}

/**
 * The account that administers the instance.
 *
 * Asked for by what it may do rather than by name: the admin area is gated on the instance user
 * type, which no station permission implies, and the seeder is free to rename the account.
 */
export async function instanceAdmin(request: APIRequestContext): Promise<DemoAccount> {
    const accounts = await demoAccounts(request)
    const match = accounts.find(account => account.instanceAdministrator)
    if (!match) throw new Error('No demo account administers the instance')
    return match
}

/** Where the global setup leaves the session it logged in for a role. */
export function storageStatePath(role: string): string {
    return `e2e/.auth/${role}.json`
}

/**
 * The headers a story needs to ask the backend something as the person whose page it holds.
 *
 * The application sends them from what it keeps in the browser; a request made straight from the
 * page carries neither, and the server answers it as if nobody had signed in. A story that reads an
 * endpoint rather than a screen - because what it is about is the endpoint refusing - asks for these
 * first.
 */
export async function apiHeaders(page: Page): Promise<Record<string, string>> {
    // What the page keeps is planted as the application starts, and a page that has not been
    // anywhere yet has no storage to read at all - asking one refuses outright.
    if (page.url() === 'about:blank') await page.goto('/station/dashboard/overview')

    const session = await page.evaluate(() => ({
        token: window.localStorage.getItem('session_token'),
        station: window.localStorage.getItem('station_id'),
    }))
    if (!session.token) throw new Error('The page holds no session to ask the backend with')

    const headers: Record<string, string> = {Authorization: `Bearer ${session.token}`}
    if (session.station) headers['X-Station-Id'] = session.station
    return headers
}

/**
 * Opens a page already carrying the role's session.
 *
 * The state holds both the token and the chosen station, which is what the application itself
 * stores after a login: a session alone leaves the station area redirecting to the station picker,
 * so a fixture that plants only the token lands every story on the wrong page.
 */
export async function pageAs(browser: Browser, role: 'manager' | 'member' | 'admin'): Promise<Page> {
    const context = await browser.newContext({storageState: storageStatePath(role)})
    return context.newPage()
}

/**
 * A page logged in as an account nobody else is using.
 *
 * The stored sessions are shared by every story that asks for a role, so a story that ends a
 * session - logging out is the obvious one - would pull the ground from under every other story
 * running at that moment. Such a story takes an account of its own instead, and logs it in itself.
 */
export async function pageAsThrowaway(
    browser: Browser,
    request: APIRequestContext,
    taken: string[],
    named?: DemoAccount,
): Promise<Page> {
    const accounts = await demoAccounts(request)
    // An address is what the login goes by, and a station holds members who never sign in: somebody
    // imported from a list of names has no way in, and picking them would fail as a login rather
    // than as what the story is about.
    const account = named ?? accounts.find(candidate =>
        candidate.userType === 'MEMBER'
        && !!candidate.email
        && candidate.stationId
        && !taken.includes(candidate.email))
    if (!account) throw new Error('No spare member account to log out with')

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

/**
 * A manager of some other station, for the stories about two stations meeting. Federation is only
 * itself when both sides are real: one station offering something and another seeing it.
 */
export async function otherStationManager(
    request: APIRequestContext,
    notStationId?: string,
    notEmail?: string,
): Promise<DemoAccount> {
    const accounts = await demoAccounts(request)
    // A different station is not enough: one person can run two of them, and acting as the same
    // account under a second station proves nothing about two stations meeting.
    const match = accounts.find(account => !!account.email
        && !!account.stationId
        && account.stationId !== notStationId
        && account.email !== notEmail
        && (account.permissions.includes('STATION_ADMINISTRATOR') || account.permissions.includes('STATION_MANAGER')))
    if (!match) throw new Error('No second station has a manager of its own to act as')
    return match
}

interface Fixtures {
    managerPage: Page
    memberPage: Page
    adminPage: Page
    partnerManagerPage: Page
}

export const test = base.extend<Fixtures>({
    managerPage: async ({browser}, use) => {
        const page = await pageAs(browser, 'manager')
        await use(page)
        await page.context().close()
    },

    memberPage: async ({browser}, use) => {
        const page = await pageAs(browser, 'member')
        await use(page)
        await page.context().close()
    },

    adminPage: async ({browser}, use) => {
        const page = await pageAs(browser, 'admin')
        await use(page)
        await page.context().close()
    },

    partnerManagerPage: async ({browser, request}, use) => {
        const {manager} = await stationPeers(request)
        const other = await otherStationManager(request, manager.stationId, manager.email)
        const page = await pageAsThrowaway(browser, request, [], other)
        await use(page)
        await page.context().close()
    },
})

export {expect} from '@playwright/test'
