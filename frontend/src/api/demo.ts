/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {DemoAccountsResponse, DemoStatusResponse} from './generated/schema'

export async function getDemoStatus(): Promise<DemoStatusResponse> {
    const res = await client.get<DemoStatusResponse>('/demo/status')
    return res.data
}

export async function getDemoAccounts(): Promise<DemoAccountsResponse> {
    const res = await client.get<DemoAccountsResponse>('/demo/accounts')
    return res.data
}
