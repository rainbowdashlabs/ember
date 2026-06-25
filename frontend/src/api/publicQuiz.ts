/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/**
 * Picker shape for catalogs marked {@code public_render = true}. Backs the QUIZ_TEASER cell
 * editor and the renderer alike.
 */
export interface PublicQuizCatalog {
    id: number
    name: string
    description: string | null
}

/**
 * Single random question payload. The {@code config} object is opaquely typed because every
 * question variant carries different fields; the renderer dispatches on {@code questionType}.
 */
export interface PublicQuizQuestion {
    id: number
    questionType: string
    title: string
    description: string | null
    imageUrl: string | null
    config: Record<string, unknown>
}

export async function listPublicCatalogs(stationUid: string): Promise<PublicQuizCatalog[]> {
    const res = await client.get<PublicQuizCatalog[]>(`/public/quiz/${stationUid}/catalogs`)
    return res.data
}

export async function getRandomPublicQuestion(stationUid: string, catalogIds: number[]): Promise<PublicQuizQuestion> {
    const params = {catalogs: catalogIds.join(','), _t: Date.now()}
    const res = await client.get<PublicQuizQuestion>(`/public/quiz/${stationUid}/random`, {params})
    return res.data
}
