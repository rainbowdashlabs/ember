/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {findEmbeddedNews} from './embeddedNewsLookup'
import {sessionWriter} from '@/util/sessionState'
import {createSessionInfo} from '@/test/mocks/factories'

const getPublicNewsTeaser = vi.fn()
const getMemberNewsTeaser = vi.fn()

vi.mock('@/api/news', () => ({
    getPublicNewsTeaser: (apiBase: string, stationUid: string, newsUid: string) =>
        getPublicNewsTeaser(apiBase, stationUid, newsUid),
    getMemberNewsTeaser: (newsUid: string) => getMemberNewsTeaser(newsUid),
}))

const STATION = 'station-a'
const NEWS_UID = 'news-1'
const TEASER = {id: 7, publicUid: NEWS_UID, title: 'Drehleiter', summary: '', publishedAt: null}

function signedInTo(stationId: string) {
    sessionWriter().setInfo(createSessionInfo({stationId}))
}

/**
 * How a news block finds its entry: the public blog on every public page and for every reader outside
 * the station, the member lookup only in a news or wiki article and only for a member of the station
 * that owns it. A 404 is "not here"; anything else is a failure that says nothing about the entry.
 */
describe('findEmbeddedNews', () => {
    beforeEach(() => {
        getPublicNewsTeaser.mockReset()
        getMemberNewsTeaser.mockReset()
    })

    afterEach(() => {
        sessionWriter().setInfo(null)
    })

    it('reads the public blog on a page, even for a member', async () => {
        signedInTo(STATION)
        getPublicNewsTeaser.mockResolvedValue(TEASER)

        const found = await findEmbeddedNews('/api/v1', STATION, NEWS_UID, 'PUBLIC')

        expect(found?.source).toEqual({kind: 'PUBLIC', stationUid: STATION})
        expect(getMemberNewsTeaser).not.toHaveBeenCalled()
    })

    it('asks the station itself for a member reading an article', async () => {
        signedInTo(STATION)
        getMemberNewsTeaser.mockResolvedValue(TEASER)

        const found = await findEmbeddedNews('/api/v1', STATION, NEWS_UID, 'MEMBERS')

        expect(found?.source).toEqual({kind: 'MEMBER'})
        expect(getPublicNewsTeaser).not.toHaveBeenCalled()
    })

    it('reads the public blog for a reader of another station, even in an article', async () => {
        signedInTo('station-b')
        getPublicNewsTeaser.mockResolvedValue(TEASER)

        expect((await findEmbeddedNews('/api/v1', STATION, NEWS_UID, 'MEMBERS'))?.source.kind).toBe('PUBLIC')
        expect(getMemberNewsTeaser).not.toHaveBeenCalled()
    })

    it('finds nothing where the entry is not here for these readers', async () => {
        signedInTo(STATION)
        getMemberNewsTeaser.mockRejectedValue({response: {status: 404}})
        getPublicNewsTeaser.mockRejectedValue({statusCode: 404})

        expect(await findEmbeddedNews('/api/v1', STATION, NEWS_UID, 'MEMBERS')).toBeNull()
        expect(await findEmbeddedNews('/api/v1', STATION, NEWS_UID, 'PUBLIC')).toBeNull()
    })

    it('passes on any other failure', async () => {
        getPublicNewsTeaser.mockRejectedValue({statusCode: 502})

        await expect(findEmbeddedNews('/api/v1', STATION, NEWS_UID, 'PUBLIC')).rejects.toEqual({statusCode: 502})
    })
})
