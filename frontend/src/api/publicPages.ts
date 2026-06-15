/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {StationPage} from './pageManage'

export interface PublicPageSummary {
    id: number
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

export function publicPageImageUrl(stationUid: string, imageId: number): string {
    return `/api/v1/public/pages/${stationUid}/images/${imageId}`
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
