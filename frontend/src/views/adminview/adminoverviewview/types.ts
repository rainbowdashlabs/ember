/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
export interface RecentApplication {
    id: number
    name: string
    station_name: string
    created_at: string
}

export interface RecentProblemReport {
    id: number
    reporter_name: string
    page_url?: string | null
    created_at: string
}

export interface AdminOverview {
    emailFailed: number
    emailPending: number
    emailStuckSending: number
    stationApplicationsPending: number
    stationsSetupPending: number
    accountsUnverified: number
    federationPartnersPending: number
    discoveryPeersUnreachable: number
    problemReportsOpen: number
    recentApplications: RecentApplication[]
    recentProblemReports: RecentProblemReport[]
}

/**
 * One tile of the attention grid: what it counts, when it turns warning or
 * critical and which admin page it links to.
 */
export interface AttentionCardSpec {
    key: string
    icon: string[]
    label: string
    count: number
    warnAt?: number
    critAt?: number
    routeName?: string
}

/**
 * One row of a recent-activity list: a headline, an optional detail line and the
 * moment the entry was created.
 */
export interface RecentEntry {
    id: number
    title: string
    detail?: string | null
    createdAt: string
}
