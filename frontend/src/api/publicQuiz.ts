/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {PublicQuizCatalog, PublicQuizQuestion} from './generated/schema'

export async function listPublicCatalogs(stationUid: string): Promise<PublicQuizCatalog[]> {
    const res = await client.get<PublicQuizCatalog[]>(`/public/quiz/${stationUid}/catalogs`)
    return res.data
}

export async function getRandomPublicQuestion(stationUid: string, catalogIds: number[]): Promise<PublicQuizQuestion> {
    const params = {catalogs: catalogIds.join(','), _t: Date.now()}
    const res = await client.get<PublicQuizQuestion>(`/public/quiz/${stationUid}/random`, {params})
    return res.data
}
