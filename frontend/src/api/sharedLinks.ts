/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {SharedBrand, SharedPage} from '@/api/generated/schema'

export async function getSharedPage(token: string): Promise<SharedPage> {
    const res = await client.get<SharedPage>(`/public/shared/${token}`)
    return res.data
}

/**
 * The branding alone. The theme is chosen while the server renders, before the page itself has been
 * fetched, so it cannot wait for the answer above.
 */
export async function getSharedPageBrand(token: string): Promise<SharedBrand> {
    const res = await client.get<SharedBrand>(`/public/shared/${token}/brand`)
    return res.data
}
