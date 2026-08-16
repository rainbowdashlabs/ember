/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/**
 * What kind of instance this is. A demo one seeds itself and resets when it goes idle; a dev one
 * additionally exposes the endpoints that only make sense while building the application.
 */
export interface DemoStatus {
    demo: boolean
    dev: boolean
}

export async function getDemoStatus(): Promise<DemoStatus> {
    const res = await client.get<DemoStatus>('/demo/status')
    return res.data
}
