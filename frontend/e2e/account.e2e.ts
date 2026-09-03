/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext} from '@playwright/test'
import {
    test,
    expect,
    accountWithout,
    demoAccounts,
    demoStationGroups,
    pageAsThrowaway,
    stationPeers,
    type DemoAccount,
    type DemoStationGroup,
} from './fixtures/auth'

/**
 * Logging in is the one story that walks the login screen itself; every other story in the suite
 * takes its session from the fixture instead.
 *
 * Logging out ends a session, and the stored sessions are shared by the whole suite - so that story
 * takes an account nobody else is using. Ending someone else's session mid-run makes every member story in
 * flight fail at once, and looks exactly like flakiness.
 */

/** How the account cards name somebody. */
function fullName(account: DemoAccount): string {
    return `${account.firstName} ${account.lastName}`
}

/**
 * Somebody who exists at exactly one station, and not the one the picker opens on.
 *
 * The demo builds the same station twice, so most names are at two of them and prove nothing about
 * which station is on offer. A name belonging to one station alone does: it is out of sight until
 * that station is picked. Whoever that is, is the seeder's business and is read rather than named.
 */
async function somebodyAtOneStationOnly(
    request: APIRequestContext,
): Promise<{group: DemoStationGroup; only: string}> {
    const groups = await demoStationGroups(request)
    const stationsPerName = new Map<string, number>()
    for (const group of groups) {
        for (const account of group.accounts ?? []) {
            stationsPerName.set(fullName(account), (stationsPerName.get(fullName(account)) ?? 0) + 1)
        }
    }

    for (const group of groups.slice(1)) {
        // Not somebody acting for an association either: those are offered in a band of their own,
        // above the picker and whichever station it stands on
        const alone = (group.accounts ?? []).find(account =>
            stationsPerName.get(fullName(account)) === 1 && !(account.clusterPermissions ?? []).length)
        if (alone && group.stationName) return {group, only: fullName(alone)}
    }
    throw new Error('Every demo account is offered at more than one station')
}

/** Somebody the demo carries at more than one station, and the stations they are at. */
async function somebodyAtSeveralStations(
    request: APIRequestContext,
): Promise<{name: string; stations: string[]}> {
    const groups = await demoStationGroups(request)
    const stationsPerName = new Map<string, string[]>()
    for (const group of groups) {
        for (const account of group.accounts ?? []) {
            const at = stationsPerName.get(fullName(account)) ?? []
            at.push(group.stationName ?? '')
            stationsPerName.set(fullName(account), at)
        }
    }

    for (const [name, stations] of stationsPerName) {
        if (stations.length > 1) return {name, stations}
    }
    throw new Error('No demo account is carried at more than one station')
}

test.describe('Account & session', () => {
    /**
     * The consent gate comes first on a fresh browser and the one-click accounts only appear behind
     * it, which is the order a real first visit meets them too.
     */
    test('logging in reaches the station', async ({page, request}) => {
        const account = await accountWithout(request, 'MEMBER', 'STATION_ADMINISTRATOR')

        await page.goto('/login')
        await page.getByRole('button', {name: 'Zustimmen'}).click()

        await page.getByText(`${account.firstName} ${account.lastName}`).first().click()

        await page.waitForURL(url => !url.pathname.startsWith('/login'))
        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /**
     * The demo instance carries more than one station in full, so the accounts on offer are the
     * picked station's rather than all of them. The picker says which station each set belongs to and
     * how many people it holds, and picking another one changes who is offered.
     */
    test('the demo login offers the people of the station that is picked', async ({page, request}) => {
        const {group, only} = await somebodyAtOneStationOnly(request)

        await page.goto('/login')
        await page.getByRole('button', {name: 'Zustimmen'}).click()

        const choice = page.getByTestId('demo-station-choice').filter({hasText: group.stationName!})
        await expect(choice).toBeVisible()
        await expect(choice).toContainText(String(group.accounts!.length))

        await expect(page.getByText(only)).toHaveCount(0)
        await choice.click()
        await expect(page.getByText(only).first()).toBeVisible()
    })

    /**
     * The same person exists at both full stations, at an address of their own, which is what makes the
     * two comparable. Searching for them turns up both, each under the station they are at, because
     * which station somebody is at is the answer being looked for rather than something to know first.
     */
    test('the demo login searches past the station that is picked', async ({page, request}) => {
        const {name, stations} = await somebodyAtSeveralStations(request)

        await page.goto('/login')
        await page.getByRole('button', {name: 'Zustimmen'}).click()

        await page.getByPlaceholder('Name, Adresse, Rolle, Gruppe oder Merkmal suchen…').fill(name)

        const results = page.getByTestId('demo-search-results')
        for (const station of stations) {
            await expect(results.getByText(station, {exact: true})).toBeVisible()
        }
        await expect(results.getByText(name)).toHaveCount(stations.length)
    })

    test('an unauthenticated visitor is sent to the login', async ({page}) => {
        await page.goto('/login')
        await page.evaluate(() => window.localStorage.clear())

        await page.goto('/station/members/list')

        await expect(page).toHaveURL(/\/login/)
    })

    /**
     * The link in a change-of-address mail points here, and the page has to exist without a
     * session: whoever clicks it is reading their mail, not sitting in the application.
     */
    test('the change-of-address link lands on a page rather than a 404', async ({page}) => {
        await page.goto('/login')
        await page.evaluate(() => window.localStorage.clear())

        await page.goto('/confirm-email-change?token=not-a-real-token')

        await expect(page).toHaveURL(/\/confirm-email-change/)
        await expect(page.getByText(/ungültig, abgelaufen oder wurde bereits verwendet/)).toBeVisible()
    })

    test('logging out ends the session', async ({browser, request}) => {
        // A member of the second seeded station: the passkey stories own fixed members of the
        // shared one, and logging one of those out mid-story would pull the ground from under
        // it. The .nord suffix is what separates the two seeds' people.
        const {member} = await stationPeers(request)
        const accounts = await demoAccounts(request)
        const loner = accounts.find(candidate =>
            candidate.userType === 'MEMBER'
            && !!candidate.email
            && !!candidate.stationId
            && candidate.email.endsWith('.nord.local'))
        const page = await pageAsThrowaway(browser, request, [member.email], loner)

        await page.goto('/station/dashboard/overview')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await page.getByTestId('account-menu').click()
        // Scoped to the header: signing off an appointment is called the same thing, and the
        // dashboard behind the menu is full of those buttons.
        await page.getByRole('banner').getByRole('button', {name: 'Abmelden'}).click()

        await page.waitForURL(/\/login/)
        await page.goto('/station/dashboard/overview')
        await page.waitForURL(/\/login/)
    })
})
