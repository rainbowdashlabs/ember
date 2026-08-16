/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {request, type FullConfig} from '@playwright/test'
import {mkdir, writeFile} from 'node:fs/promises'
import {dirname} from 'node:path'
import {stationPeers, storageStatePath} from './fixtures/auth'

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

export default async function globalSetup(config: FullConfig) {
    const baseURL = config.projects[0]?.use?.baseURL ?? 'http://localhost:3000'

    const context = await request.newContext({baseURL})
    const {manager, member} = await stationPeers(context).finally(() => context.dispose())

    await saveSession(baseURL, manager.email, manager.stationId, 'manager')
    await saveSession(baseURL, member.email, member.stationId, 'member')
}
