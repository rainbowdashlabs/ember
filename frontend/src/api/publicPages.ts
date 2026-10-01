/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {PublicPageSummary, PublicPartnerSummary, StationPage} from '@/api/generated/schema'

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
