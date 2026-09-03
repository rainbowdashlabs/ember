/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {createHmac} from 'node:crypto'
import {readFile} from 'node:fs/promises'
import {MADE_BY_A_STORY} from './cluster'
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
    /** Everything the account may do for any cluster, expanded. Empty for somebody in no cluster. */
    clusterPermissions?: string[]
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
 * The identity a role was pinned to at global setup, read back from its stored session.
 *
 * Asked from the file rather than recomputed, because the discovery that picked it is not stable
 * across a run: stories create stations, and one made by importing a transfer holds the very same
 * accounts as the seeded one. A story that recomputed would drift to whichever station the
 * response lists first at that moment. The dev instance issues the account's address as its
 * session token, which is what makes the file carry the identity at all.
 */
export async function pinnedRole(role: 'manager' | 'member' | 'admin'): Promise<{email: string; stationId?: string}> {
    const state = JSON.parse(await readFile(storageStatePath(role), 'utf-8')) as {
        origins?: {localStorage?: {name: string; value: string}[]}[]
    }
    const entries = state.origins?.[0]?.localStorage ?? []
    const value = (name: string) => entries.find(entry => entry.name === name)?.value
    const email = value('session_token')
    if (!email || !email.includes('@')) throw new Error(`The ${role} storage state holds no dev session token`)
    return {email, stationId: value('station_id')}
}

/** The one password every seeded account shares. */
export const DEMO_PASSWORD = 'demo'

/** The knowable TOTP secret the demo seeder enrols, for the accounts whose proof is a code. */
export const DEMO_TOTP_SECRET = 'JBSWY3DPEHPK3PXP'

function base32Decode(encoded: string): Buffer {
    const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
    let bits = 0
    let value = 0
    const bytes: number[] = []
    for (const char of encoded.replace(/=+$/, '')) {
        value = (value << 5) | alphabet.indexOf(char)
        bits += 5
        if (bits >= 8) {
            bytes.push((value >>> (bits - 8)) & 0xff)
            bits -= 8
        }
    }
    return Buffer.from(bytes)
}

/** The six digits an authenticator holding the seeded secret would show right now. */
export function demoTotpCode(now = Date.now()): string {
    const counter = Buffer.alloc(8)
    counter.writeBigUInt64BE(BigInt(Math.floor(now / 1000 / 30)))
    const digest = createHmac('sha1', base32Decode(DEMO_TOTP_SECRET)).update(counter).digest()
    const offset = digest[digest.length - 1]! & 0xf
    const code = (((digest[offset]! & 0x7f) << 24)
        | (digest[offset + 1]! << 16)
        | (digest[offset + 2]! << 8)
        | digest[offset + 3]!) % 1_000_000
    return code.toString().padStart(6, '0')
}

/**
 * Answers the step-up prompt with whatever proof it asks for, wherever it appears: the seeded
 * password where the account has no second factor, the code from the seeded knowable TOTP secret
 * where it has one.
 *
 * Step-up asks every account for a fresh proof on the sensitive routes, and the stories meet it
 * like a member would: the dialog opens, the proof goes in, the request is retried. Answered
 * here rather than per story, because a dev session is one row per account replaced on every
 * sign-in - a second worker signing in as the same person wipes the first one's freshness, and
 * the five-minute window expires mid-run anyway, so the prompt can arrive in any story at any
 * moment. Only the dev suite runs this rule at all; the public demo instance is exempt.
 */
export async function answerStepUpPrompts(page: Page): Promise<void> {
    const dialog = page.getByRole('dialog').filter({hasText: 'Sicherheitsbestätigung erforderlich'})
    await page.addLocatorHandler(dialog, async () => {
        // A code answered right at a period boundary is stale by the time it arrives, and a code
        // another story spent already counts as used; later rounds answer with the next period's
        // code, which the drift window accepts.
        for (let attempt = 0; attempt < 3; attempt++) {
            const passwordField = dialog.getByPlaceholder('Dein Passwort')
            if (await passwordField.count() > 0) {
                await passwordField.fill(DEMO_PASSWORD)
            } else {
                await dialog.getByPlaceholder('000000').fill(demoTotpCode(Date.now() + (attempt % 2) * 30_000))
            }
            await dialog.getByRole('button', {name: 'Bestätigen', exact: true}).click()
            try {
                await dialog.waitFor({state: 'hidden', timeout: 5_000})
                return
            } catch { /* still open: answer it again */ }
        }
    }, {times: 20})
}

/**
 * Stamps the page's session as freshly proved, for a story that asks a guarded endpoint straight
 * over the API rather than through a screen: no dialog appears there, so the proof is given
 * up front. Good for the freshness window, which outlives any single story.
 */
export async function freshStepUpProof(page: Page): Promise<void> {
    const headers = await apiHeaders(page)
    const response = await page.request.post('/api/v1/auth/stepup/password', {
        headers,
        data: {password: DEMO_PASSWORD},
    })
    if (response.ok()) return
    // An account with a second factor is refused the password on purpose; it proves itself with
    // the code from the seeded knowable secret instead. A code only counts once per period, and
    // several stories can prove the same account inside one, so the refusal is answered with the
    // next period's code, which the drift window accepts.
    if (response.status() === 403) {
        for (const ahead of [0, 30_000]) {
            const totp = await page.request.post('/api/v1/auth/2fa/stepup', {
                headers,
                data: {factor: 'TOTP', proof: demoTotpCode(Date.now() + ahead)},
            })
            if (totp.ok()) return
            // Throttled: the second code would only be refused the same way. The caller waits
            // and asks again; often a sibling story's proof has covered it by then.
            if (totp.status() === 429) break
        }
        throw new Error('The TOTP step-up refused both the current and the next code')
    }
    throw new Error(`The password step-up answered ${response.status()}`)
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
    const page = await context.newPage()
    await answerStepUpPrompts(page)
    return page
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
    const page = await context.newPage()
    await answerStepUpPrompts(page)
    return page
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

/**
 * The clusters an account may act for, asked as that account.
 *
 * A story that walks into the cluster area has to name which cluster it means, because the identity
 * travels on the header and nothing guesses it. Read rather than hardcoded, for the same reason the
 * demo accounts are: the seeder is free to rename what it builds.
 */
export async function clustersOf(page: Page): Promise<Cluster[]> {
    const headers = await apiHeaders(page)
    const response = await page.request.get('/api/v1/clusters', {headers})
    if (!response.ok()) throw new Error(`The cluster list answered ${response.status()}`)
    return response.json()
}

/**
 * A cluster as a story needs to know it: what to name, and the station it keeps its own things on.
 *
 * A cluster writes its knowledge base, its news and its calendar with the ordinary station screens, over
 * the station it owns. Writing them in a story therefore means naming that station the same way the
 * application does, which is why the id is part of what a story is handed.
 */
export interface Cluster {
    uid: string
    name: string
    homeStationId: string
}

/**
 * The headers that write for a cluster: its identity, and the station its own things live on.
 *
 * @param page    a signed-in page
 * @param cluster the cluster being written for
 */
export async function clusterHeaders(page: Page, cluster: Cluster): Promise<Record<string, string>> {
    return {
        ...await apiHeaders(page),
        'X-Cluster-Id': cluster.uid,
        'X-Station-Id': cluster.homeStationId,
    }
}

/**
 * A page already inside the cluster area, acting for the first cluster its account may act for.
 *
 * The application plants the same key when somebody uses the switcher; a story that is not about
 * the switcher plants it directly rather than clicking through it every time.
 */
export async function enterCluster(page: Page): Promise<Cluster> {
    const cluster = await theSeededCluster(page)
    await page.evaluate(uid => window.localStorage.setItem('cluster_id', uid), cluster.uid)
    return cluster
}

/**
 * The cluster the demo is about, told apart from the ones the stories make.
 *
 * Several stories create a cluster of their own to govern, and the administrator is appointed to every one
 * of them, so "the first cluster this account may act for" stops meaning anything the moment two stories
 * run at once. The seeded one is the only one with stations under it, which is also what makes it the one
 * worth telling a story about.
 */
export async function theSeededCluster(page: Page): Promise<Cluster> {
    const clusters = await clustersOf(page)
    if (!clusters.length) throw new Error('This account may act for no cluster')

    // By name rather than by asking each one what it governs: reading a cluster's stations needs a right
    // the narrower cluster roles do not hold, and telling them apart is not something a story should need
    // a permission for.
    const seeded = clusters.find(cluster => !cluster.name.startsWith(MADE_BY_A_STORY))
    if (!seeded) throw new Error('Every cluster this account may act for was made by a story')
    return seeded
}

/**
 * A demo account holding a cluster right, so a story names the rights it needs rather than a person.
 *
 * The seeder gives the cluster an administrator, somebody who only looks after members and somebody who
 * only looks after gear. A story asking for the narrowest of the three is what makes the permission part
 * of the test instead of an accident.
 */
export async function clusterAccountWith(request: APIRequestContext, permission: string): Promise<DemoAccount> {
    const accounts = await demoAccounts(request)
    const match = accounts.find(account =>
        !!account.email && (account.clusterPermissions ?? []).includes(permission))
    if (!match) throw new Error(`No demo account may ${permission} for a cluster`)
    return match
}

/**
 * The narrowest cluster account that can do the thing, preferring one that is not the administrator.
 *
 * An administrator holds every right, so asking only for the right would keep finding them and the story
 * would never prove the right does anything on its own.
 */
export async function clusterAccountOnlyWith(request: APIRequestContext, permission: string): Promise<DemoAccount> {
    const accounts = await demoAccounts(request)
    const holders = accounts.filter(account =>
        !!account.email && (account.clusterPermissions ?? []).includes(permission))
    if (!holders.length) throw new Error(`No demo account may ${permission} for a cluster`)
    return holders.reduce((narrowest, account) =>
        (account.clusterPermissions ?? []).length < (narrowest.clusterPermissions ?? []).length ? account : narrowest)
}

/**
 * A page signed in as the given account, with the cluster area already entered.
 *
 * Its own context rather than one of the three shared ones, because the cluster roles are held by demo
 * members the station stories are also acting as, and planting a cluster into a shared session would
 * follow those stories around.
 */
export async function clusterPage(
    browser: Browser,
    request: APIRequestContext,
    account: DemoAccount,
): Promise<Page> {
    const login = await request.post('/api/v1/demo/login', {data: {email: account.email}})
    if (!login.ok()) throw new Error(`Demo login for ${account.email} answered ${login.status()}`)
    const {token} = await login.json()

    const context = await browser.newContext()
    await context.addInitScript(([sessionToken, stationId]) => {
        window.localStorage.setItem('session_token', sessionToken)
        if (stationId) window.localStorage.setItem('station_id', stationId)
        window.localStorage.setItem('storage_consent', 'accepted')
    }, [token, account.stationId ?? ''])
    const page = await context.newPage()
    await answerStepUpPrompts(page)
    await enterCluster(page)
    return page
}

/**
 * The stations a cluster has, asked as somebody who may act for it.
 *
 * A story about what the cluster reaches needs to know which stations those are, and the names are the
 * seeder's to change.
 */
export async function clusterStations(page: Page): Promise<{uid: string; name: string}[]> {
    const headers = await apiHeaders(page)
    const cluster = await enterCluster(page)
    const response = await page.request.get('/api/v1/cluster/stations', {
        headers: {...headers, 'X-Cluster-Id': cluster.uid},
    })
    if (!response.ok()) throw new Error(`The cluster station list answered ${response.status()}`)
    return response.json()
}

/** A station the instance offers demo logins for, with everybody at it. */
export interface DemoStationGroup {
    stationId?: string
    stationName?: string
    accounts?: DemoAccount[]
}

/**
 * The demo's stations, each with the accounts at it, in the order the instance lists them.
 *
 * Grouped rather than flattened, because a story looking for a station of a certain kind needs the
 * station and not just the people: which station is which is the seeder's business, and every fixture
 * below finds one by asking rather than by name.
 */
export async function demoStationGroups(request: APIRequestContext): Promise<DemoStationGroup[]> {
    const response = await request.get('/api/v1/demo/accounts')
    if (!response.ok()) throw new Error(`The demo accounts endpoint answered ${response.status()}`)
    return (await response.json()).stationGroups ?? []
}

/** Whoever at this station may act for it. */
function managersOf(group: DemoStationGroup): DemoAccount[] {
    return (group.accounts ?? []).filter(account => !!account.email
        && (account.permissions.includes('STATION_ADMINISTRATOR')
            || account.permissions.includes('STATION_MANAGER')))
}

/**
 * The cluster a station answers to, asked as one of its managers.
 *
 * @returns what the station says about its cluster, or null when this manager cannot ask
 */
async function clusterOf(
    request: APIRequestContext,
    group: DemoStationGroup,
    account: DemoAccount,
): Promise<{clusterUid?: string; clusterName?: string} | null> {
    const login = await request.post('/api/v1/demo/login', {data: {email: account.email}})
    if (!login.ok()) return null
    const {token} = await login.json()
    const cluster = await request.get('/api/v1/station/cluster', {
        headers: {Authorization: `Bearer ${token}`, 'X-Station-Id': group.stationId ?? ''},
    })
    return cluster.ok() ? cluster.json() : null
}

/**
 * A manager of a station that answers to a cluster.
 *
 * The station stories pick whichever station has both a manager and a member, and that is not
 * necessarily one inside a cluster: the demo deliberately leaves two outside. A story about what a
 * cluster does to a station has to be at one of the stations it actually governs.
 */
export async function clusterStationManager(request: APIRequestContext): Promise<DemoAccount> {
    for (const group of await demoStationGroups(request)) {
        for (const account of managersOf(group)) {
            const cluster = await clusterOf(request, group, account)
            // Not a station some other story built for itself: those come and go while this one reads
            if (cluster?.clusterUid && !String(cluster.clusterName ?? '').startsWith(MADE_BY_A_STORY)) {
                return {...account, stationId: group.stationId}
            }
        }
    }
    throw new Error('No station inside a cluster has a manager of its own to act as')
}

/**
 * An ordinary member of the station that answers to a cluster.
 *
 * The counterpart of {@link clusterStationManager}: a story where somebody has a piece of the cluster's
 * gear in their hands needs the two of them at the same station, and the shared member fixture is at the
 * station standing outside every cluster.
 */
export async function clusterStationMember(request: APIRequestContext): Promise<DemoAccount> {
    const manager = await clusterStationManager(request)
    const accounts = await demoAccounts(request)
    const member = accounts.find(account => !!account.email
        && account.stationId === manager.stationId
        && account.userType === 'MEMBER')
    if (!member) throw new Error('The station inside a cluster has no ordinary member to act as')
    return member
}

/**
 * A manager of the full demo station that answers to nobody.
 *
 * The demo builds the same station twice, one inside an association and one outside it, so that every
 * feature can be looked at both ways. This is the outside half, and it is the one the stories about
 * standing alone act on. Of the stations in no cluster it is the one carrying the demo's whole cast,
 * which the spare stations beside it come nowhere near.
 */
export async function standaloneStationManager(request: APIRequestContext): Promise<DemoAccount> {
    const groups = [...await demoStationGroups(request)]
        .sort((first, second) => (second.accounts?.length ?? 0) - (first.accounts?.length ?? 0))
    for (const group of groups) {
        for (const account of managersOf(group)) {
            const cluster = await clusterOf(request, group, account)
            if (cluster && !cluster.clusterUid) return {...account, stationId: group.stationId}
        }
    }
    throw new Error('No full station stands outside every cluster')
}

/**
 * A page signed in as the person who looks after the cluster's gear, acting for the cluster.
 *
 * Its own context because the account is also somebody at a station, and planting a cluster into the
 * shared session would follow the station stories around. The station header travels too: the same person
 * works a station queue and the cluster's, and which one they mean is the header's job to say.
 */
export async function clusterGearManagerPage(browser: Browser, request: APIRequestContext): Promise<Page> {
    const account = await clusterAccountOnlyWith(request, 'CLUSTER_INVENTORY_EXCHANGE')
    return clusterPage(browser, request, account)
}

interface Fixtures {
    managerPage: Page
    memberPage: Page
    adminPage: Page
    partnerManagerPage: Page
    /** A manager of a station that answers to a cluster, which the shared manager is not. */
    clusterStationManagerPage: Page
    /** An ordinary member of that same station, for the stories that need both. */
    clusterStationMemberPage: Page
    /** A manager of the full station that answers to nobody, which is the other half of the demo. */
    standaloneStationManagerPage: Page
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

    clusterStationManagerPage: async ({browser, request}, use) => {
        const page = await pageAsThrowaway(browser, request, [], await clusterStationManager(request))
        await use(page)
        await page.context().close()
    },

    clusterStationMemberPage: async ({browser, request}, use) => {
        const page = await pageAsThrowaway(browser, request, [], await clusterStationMember(request))
        await use(page)
        await page.context().close()
    },

    standaloneStationManagerPage: async ({browser, request}, use) => {
        const page = await pageAsThrowaway(browser, request, [], await standaloneStationManager(request))
        await use(page)
        await page.context().close()
    },
})

export {expect} from '@playwright/test'
