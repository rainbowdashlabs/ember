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

/**
 * A demo account offered for one-click login, as the instance describes it.
 */
export interface DemoAccount {
    email: string
    firstName: string
    lastName: string
    userType: string
    permissions: string[]
    groups: string[]
    tags: string[]
    profileComplete: boolean
    instanceAdministrator?: boolean
    /** Everything the account may do for any association, expanded. Empty for somebody in none. */
    clusterPermissions?: string[]
}

/** A station and the demo accounts that belong to it. */
export interface DemoStationGroup {
    stationId: string
    stationName: string
    accounts: DemoAccount[]
}

/**
 * The accounts, in whichever shape the instance answers with: grouped by station, a bare list of
 * groups, or a flat list from an older instance.
 */
export type DemoAccountsPayload =
    | {noStationAccounts: DemoAccount[]; stationGroups: DemoStationGroup[]}
    | DemoStationGroup[]
    | DemoAccount[]

export async function getDemoAccounts(): Promise<DemoAccountsPayload> {
    const res = await client.get<DemoAccountsPayload>('/demo/accounts')
    return res.data
}
