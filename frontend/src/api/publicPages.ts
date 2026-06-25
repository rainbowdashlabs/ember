/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {StationPage} from './pageManage'

export interface PublicPageSummary {
    id: number
    /** Stable opaque public identifier — referenced by PAGE_LINK cells. */
    publicUid: string
    parentId: number | null
    title: string
    slug: string
    path: string
    sortOrder: number
    metaDescription: string | null
    ogImageId: number | null
}

export async function listPublicPages(stationUid: string): Promise<PublicPageSummary[]> {
    const res = await client.get<PublicPageSummary[]>(`/public/pages/${stationUid}`)
    return res.data
}

export async function getPublicPage(stationUid: string, path: string): Promise<StationPage> {
    const res = await client.get<StationPage>(`/public/pages/${stationUid}/page/${path}`)
    return res.data
}

export async function getPublicLandingPage(stationUid: string): Promise<StationPage> {
    const res = await client.get<StationPage>(`/public/pages/${stationUid}/landing`)
    return res.data
}

export function publicPageImageUrl(stationUid: string, contentHash: string): string {
    return `/api/v1/public/pages/${stationUid}/files/${contentHash}`
}

export interface PublicPartnerSummary {
    uid: string
    name: string
    slug: string | null
    distanceKm: number | null
}

export async function listPartnerStations(stationUid: string): Promise<PublicPartnerSummary[]> {
    const res = await client.get<PublicPartnerSummary[]>(`/public/pages/${stationUid}/partners`)
    return res.data
}

export async function resolvePartnerStations(
    stationUid: string,
    uids: string[],
): Promise<PublicPartnerSummary[]> {
    if (uids.length === 0) return []
    const res = await client.get<PublicPartnerSummary[]>(`/public/pages/${stationUid}/partners`, {
        params: {uids: uids.join(',')},
    })
    return res.data
}
