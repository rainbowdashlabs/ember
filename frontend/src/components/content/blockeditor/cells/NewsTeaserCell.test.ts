/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import NewsTeaserCell from './NewsTeaserCell.vue'
import type {PublicNewsTeaser} from '@/api/news'

const getPublicNewsTeaser = vi.fn()

vi.mock('@/api/news', () => ({
    getPublicNewsTeaser: (apiBase: string, stationUid: string, newsUid: string) =>
        getPublicNewsTeaser(apiBase, stationUid, newsUid),
}))

function entry(newsUid: string): PublicNewsTeaser {
    return {
        id: 7,
        publicUid: newsUid,
        title: 'Neue Drehleiter eingeweiht',
        summary: 'Seit Samstag steht sie im Gerätehaus.',
        publishedAt: '2026-09-12T10:00:00Z',
    }
}

async function shown(newsUid: string | null) {
    const view = await mountSuspended(NewsTeaserCell, {
        props: {config: {newsUid}, stationUid: 'station-a', timezone: 'Europe/Berlin'},
    })
    await flushPromises()
    return view
}

/**
 * A news block shows the entry it names as the entry is now, read by its id from the station's
 * public blog, and calls it unavailable only when the blog says there is no such entry.
 */
describe('NewsTeaserCell', () => {
    beforeEach(() => {
        getPublicNewsTeaser.mockReset()
    })

    it('reads the entry it names and links to it on the public blog', async () => {
        const uid = '3f2b8c4e-1a6d-4e7f-9b0c-5d8e2f1a7c01'
        getPublicNewsTeaser.mockResolvedValue(entry(uid))

        const view = await shown(uid)

        expect(getPublicNewsTeaser).toHaveBeenCalledWith(expect.any(String), 'station-a', uid)
        expect(view.text()).toContain('Neue Drehleiter eingeweiht')
        expect(view.text()).toContain('Seit Samstag steht sie im Gerätehaus.')
        expect(view.get('a').attributes('href')).toBe('/public/station/station-a/blog/7')
    })

    it('says the entry is not available where the blog does not show it', async () => {
        getPublicNewsTeaser.mockRejectedValue({statusCode: 404, statusMessage: 'Not Found'})

        const view = await shown('3f2b8c4e-1a6d-4e7f-9b0c-5d8e2f1a7c02')

        expect(view.text()).toContain('Diese Neuigkeit ist hier nicht verfügbar.')
        expect(view.find('a').exists()).toBe(false)
    })

    it('does not call the entry gone when reading it failed for another reason', async () => {
        getPublicNewsTeaser.mockRejectedValue({statusCode: 502, statusMessage: 'Bad Gateway'})

        const view = await shown('3f2b8c4e-1a6d-4e7f-9b0c-5d8e2f1a7c03')

        expect(view.text()).toBe('')
    })

    it('does not call the entry gone while it is still looking', async () => {
        getPublicNewsTeaser.mockReturnValue(new Promise(() => {}))

        const view = await shown('3f2b8c4e-1a6d-4e7f-9b0c-5d8e2f1a7c04')

        expect(view.text()).toBe('')
    })

    it('asks for nothing where no entry is named', async () => {
        const view = await shown(null)

        expect(getPublicNewsTeaser).not.toHaveBeenCalled()
        expect(view.text()).toContain('Diese Neuigkeit ist hier nicht verfügbar.')
    })
})
