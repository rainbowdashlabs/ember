/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {request, type FullConfig} from '@playwright/test'
import {mkdir, writeFile} from 'node:fs/promises'
import {dirname} from 'node:path'
import {instanceAdmin, stationPeers, storageStatePath} from './fixtures/auth'

/**
 * Logs each role in once for the whole run and stores the result on disk.
 *
 * Every worker is its own process, so a per-process cache cannot stop two of them logging in as the
 * same person at the same moment — and the seeded station has exactly one manager. A dev instance
 * issues a deterministic token, so the second login collides on the session it just wrote and
 * answers 500. Doing it once, before any worker starts, removes the race rather than retrying it.
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
        const response = await context.post('/api/v1/dev/reset')
        if (!response.ok() && response.status() !== 404) {
            throw new Error(`The dev reset answered ${response.status()}`)
        }
    } finally {
        await context.dispose()
    }
}

export default async function globalSetup(config: FullConfig) {
    const baseURL = config.projects[0]?.use?.baseURL ?? 'http://localhost:3000'

    if (!process.env.E2E_KEEP_DATA) await resetData(baseURL)

    const context = await request.newContext({baseURL})
    const {manager, member} = await stationPeers(context)
    const admin = await instanceAdmin(context).finally(() => context.dispose())

    await saveSession(baseURL, manager.email, manager.stationId, 'manager')
    await saveSession(baseURL, member.email, member.stationId, 'member')
    await saveSession(baseURL, admin.email, admin.stationId, 'admin')
}
