/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {request, type FullConfig} from '@playwright/test'
import {mkdir, writeFile} from 'node:fs/promises'
import {dirname} from 'node:path'
import {instanceAdmin, stationPeers, storageStatePath} from './fixtures/auth'
import {peerBaseUrl, waitForInstance} from './fixtures/peer'

/**
 * Logs each role in once for the whole run and stores the result on disk.
 *
 * Every worker is its own process, so a per-process cache cannot stop two of them logging in as the
 * same person at the same moment - and the seeded station has exactly one manager. A dev instance
 * issues a deterministic token, so the second login collides on the session it just wrote and
 * answers 500. Doing it once, before any worker starts, removes the race rather than retrying it.
 *
 * The stored state marks the introductory tour as seen: its bar is fixed to the bottom of the
 * window and swallows clicks meant for anything anchored there.
 */
async function saveSession(baseURL: string, email: string, stationId: string | undefined, role: string) {
    const context = await request.newContext({baseURL})
    try {
        const login = await context.post('/api/v1/demo/login', {data: {email}})
        if (!login.ok()) throw new Error(`Demo login for ${email} answered ${login.status()}`)
        const {token} = await login.json()

        const path = storageStatePath(role)
        await mkdir(dirname(path), {recursive: true})
        await writeFile(path, JSON.stringify({
            cookies: [],
            origins: [{
                origin: baseURL,
                localStorage: [
                    {name: 'session_token', value: token},
                    {name: 'storage_consent', value: 'accepted'},
                    {name: 'onboarding_tour_completed', value: 'true'},
                    ...(stationId ? [{name: 'station_id', value: stationId}] : []),
                ],
            }],
        }, null, 2))
    } finally {
        await context.dispose()
    }
}

/**
 * Throws away whatever the run before this one left behind.
 *
 * The stories create boards, tickets, checklists and groups, and nothing takes them away again.
 * Without this the seeded station fills up run by run until a story that counts rows, or one that
 * picks "the first entry", starts answering about someone else's leftovers. Skipped when the
 * endpoint is absent, which is every instance that is not a dev one.
 */
async function resetData(baseURL: string) {
    const context = await request.newContext({baseURL})
    try {
        // Seeding a station from nothing takes the better part of a minute and grows with the
        // seed, so this waits far longer than a request normally would.
        const response = await context.post('/api/v1/dev/reset', {timeout: 180_000})
        if (!response.ok() && response.status() !== 404) {
            throw new Error(`The dev reset answered ${response.status()}`)
        }
    } finally {
        await context.dispose()
    }
}

/**
 * Prepares the run: both instances are emptied, and the three shared sessions are logged in once.
 *
 * The base address comes from the environment or from the first project, not from the project the
 * run happens to start with: the projects carry only their device overrides, and reading it off one
 * of them sent the setup at the default port whatever the run was actually pointed at.
 *
 * Both instances are reset, and both at once. Each throws away its own schema and migrates it back,
 * in its own container, so the two genuinely overlap: the run waits for the slower of them rather
 * than for the two of them in turn. Resetting only the first would leave the second filling up run
 * by run, which is what the reset exists to prevent.
 */
export default async function globalSetup(config: FullConfig) {
    const baseURL = process.env.E2E_BASE_URL
        ?? config.projects[0]?.use?.baseURL
        ?? 'http://localhost:3000'

    if (!process.env.E2E_KEEP_DATA) {
        await waitForInstance(peerBaseUrl())
        await Promise.all([resetData(baseURL), resetData(peerBaseUrl())])
    }

    const context = await request.newContext({baseURL})
    const {manager, member} = await stationPeers(context)
    const admin = await instanceAdmin(context).finally(() => context.dispose())

    await saveSession(baseURL, manager.email, manager.stationId, 'manager')
    await saveSession(baseURL, member.email, member.stationId, 'member')
    await saveSession(baseURL, admin.email, admin.stationId, 'admin')
}
