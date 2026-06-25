/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */

/**
 * A demo account exposed by the backend on a demo or dev instance for
 * one-click login on the login page.
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
}

/**
 * A named group of demo accounts shown together on the demo login UI
 * (e.g. "Admin", "Team", "Guardian").
 */
export interface RoleGroup {
    label: string
    accounts: DemoAccount[]
}

/**
 * A station tab in the demo login UI, used to switch between sets of
 * demo accounts that belong to different stations.
 */
export interface StationTab {
    key: string
    label: string
}
