/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {request, type APIRequestContext} from '@playwright/test'
import {demoStationGroups, instanceAdmin, test as base, type DemoAccount} from './auth'

/**
 * The second instance: a whole other installation of the application, not a second station of the
 * one the rest of the suite works in.
 *
 * Federation is between instances, and a story that calls one station of an instance the "other
 * side" proves nothing about it: both stations share a database, a session store and a version, so
 * every call that federation makes over the network is a method call there. The second instance
 * runs its own migrations in its own schema, keeps its own accounts and its own identity, and the
 * only way to it is HTTP.
 *
 * It has no frontend. Everything a story does as this instance is API work, which is why the
 * fixtures here hand out request contexts rather than pages.
 */

/** Where the stories reach the second instance: the port its container publishes on the host. */
export function peerBaseUrl(): string {
    return process.env.E2E_PEER_URL ?? 'http://localhost:8898'
}

/** Where the stories reach the first instance's backend without going through its frontend. */
export function homeBaseUrl(): string {
    return process.env.NUXT_BACKEND_URL ?? 'http://localhost:8899'
}

/**
 * The address the first instance names the second one by.
 *
 * Not the one the stories use. A backend calling another backend is a container calling a
 * container, and inside the compose network the name of the service is what resolves; `localhost`
 * there is the caller itself, which is the usual reason such a setup answers on the second attempt
 * rather than the first.
 */
export function peerInternalUrl(): string {
    return process.env.E2E_PEER_INTERNAL_URL ?? 'http://ember-e2e-peer:8080'
}

/** The address the second instance names the first one by, for the same reason. */
export function homeInternalUrl(): string {
    return process.env.E2E_HOME_INTERNAL_URL ?? 'http://ember-e2e:8080'
}

/**
 * The address the first instance publishes as its own, and hands to anybody who asks.
 *
 * The one address in this stack that is not a container address: it also has to work in a browser,
 * for the links it appears in and for the origin the second factor is bound to. It is therefore the
 * address the second instance writes down for the first when the two pair, and one the second
 * instance cannot actually call. Which is why a code always travels from the second instance to the
 * first here, and never back.
 */
export function homePublishedUrl(): string {
    return process.env.E2E_HOME_PUBLISHED_URL ?? 'http://localhost:3010'
}

/**
 * Waits until an instance answers at all.
 *
 * The stack starts both instances at once and each compiles itself from the sources beside it, so
 * the second one is still building when the first is serving. Without this the run's very first
 * act - resetting both - would fail against a port nothing listens on yet, and read as the second
 * instance being broken rather than as it being late. A refused connection is therefore the normal
 * answer for the first minutes and not a reason to stop.
 */
export async function waitForInstance(baseUrl: string, timeoutMs = 900_000): Promise<void> {
    const deadline = Date.now() + timeoutMs
    const context = await request.newContext({baseURL: baseUrl})
    try {
        for (;;) {
            const answered = await context
                .get('/api/v1/public/config', {timeout: 10_000})
                .then(response => response.ok())
                .catch(() => false)
            if (answered) return
            if (Date.now() > deadline) {
                throw new Error(
                    `The instance at ${baseUrl} did not answer within ${Math.round(timeoutMs / 1000)}s. `
                    + 'Start the stack with `./toolchain.sh docker-e2e`.',
                )
            }
            await new Promise(resolve => setTimeout(resolve, 2_000))
        }
    } finally {
        await context.dispose()
    }
}

/** An unauthenticated request context against the instance at the given address. */
export async function instanceRequest(baseUrl: string): Promise<APIRequestContext> {
    return request.newContext({baseURL: baseUrl})
}

/**
 * A request context signed in as the given account of the instance at the given address.
 *
 * The token is asked for the same way the first instance's sessions are: the demo login endpoint,
 * which a dev instance answers with a session for whoever is named. The station travels on the
 * header, because an account can be at a station and nothing guesses which one is meant.
 */
export async function instanceRequestAs(
    baseUrl: string,
    account: {email: string; stationId?: string},
): Promise<APIRequestContext> {
    const anonymous = await instanceRequest(baseUrl)
    let token: string
    try {
        const login = await anonymous.post('/api/v1/demo/login', {data: {email: account.email}})
        if (!login.ok()) {
            throw new Error(`The instance at ${baseUrl} answered ${login.status()} to a login for ${account.email}`)
        }
        token = (await login.json()).token
    } finally {
        await anonymous.dispose()
    }

    const headers: Record<string, string> = {Authorization: `Bearer ${token}`}
    if (account.stationId) headers['X-Station-Id'] = account.stationId
    return request.newContext({baseURL: baseUrl, extraHTTPHeaders: headers})
}

/** Whoever administers the instance at the given address, discovered rather than named. */
export async function adminOf(baseUrl: string): Promise<DemoAccount> {
    const context = await instanceRequest(baseUrl)
    try {
        return await instanceAdmin(context)
    } finally {
        await context.dispose()
    }
}

/**
 * Somebody who runs a station of that instance and can act without a second factor.
 *
 * The federation actions all ask for a fresh second factor, and the demo gives one person at each
 * station a real authenticator with a secret nobody holds: a story acting as that person is stopped
 * before it begins, and no amount of retrying produces the six digits. Asked by what the account is
 * rather than by name, because which of them the seeder picks is the seeder's business.
 */
export async function stationManagerOf(baseUrl: string): Promise<DemoAccount> {
    const context = await instanceRequest(baseUrl)
    try {
        for (const group of await demoStationGroups(context)) {
            for (const account of group.accounts ?? []) {
                const runsIt = account.permissions.includes('STATION_ADMINISTRATOR')
                    || account.permissions.includes('STATION_MANAGER')
                if (!account.email || !group.stationId || !runsIt) continue
                const candidate = {...account, stationId: group.stationId}
                if (await actsWithoutSecondFactor(baseUrl, candidate)) return candidate
            }
        }
    } finally {
        await context.dispose()
    }
    throw new Error(`No station on ${baseUrl} has a manager who can act without a second factor`)
}

/** Whether the account is free of the second factor that would stop a story acting as it. */
async function actsWithoutSecondFactor(baseUrl: string, account: DemoAccount): Promise<boolean> {
    const context = await instanceRequestAs(baseUrl, account)
    try {
        const status = await context.get('/api/v1/account/2fa/status')
        return status.ok() && !(await status.json()).enrolled
    } finally {
        await context.dispose()
    }
}

interface PeerFixtures {
    /** The second instance, asked as the person who administers it. */
    peerAdminApi: APIRequestContext
    /**
     * The first instance's backend, asked as the person who administers it.
     *
     * Straight at the port rather than through the frontend the other stories go by: what a story
     * here compares is two backends, and a proxy in between is one more thing able to answer for
     * the wrong one.
     */
    homeAdminApi: APIRequestContext
    /** The first instance, asked as somebody who runs one of its stations. */
    homeManagerApi: APIRequestContext
}

export const test = base.extend<PeerFixtures>({
    peerAdminApi: async ({}, use) => {
        const context = await instanceRequestAs(peerBaseUrl(), await adminOf(peerBaseUrl()))
        await use(context)
        await context.dispose()
    },

    homeAdminApi: async ({}, use) => {
        const context = await instanceRequestAs(homeBaseUrl(), await adminOf(homeBaseUrl()))
        await use(context)
        await context.dispose()
    },

    homeManagerApi: async ({}, use) => {
        const context = await instanceRequestAs(homeBaseUrl(), await stationManagerOf(homeBaseUrl()))
        await use(context)
        await context.dispose()
    },
})

export {expect} from '@playwright/test'
