/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import NewsTeaserCell from './NewsTeaserCell.vue'
import type {PublicBlogEntry} from '@/api/news'

const listPublicBlog = vi.fn()

vi.mock('@/api/news', () => ({
    listPublicBlog: (stationUid: string, offset: number, limit: number) => listPublicBlog(stationUid, offset, limit),
}))

const NEWS_UID = '3f2b8c4e-1a6d-4e7f-9b0c-5d8e2f1a7c33'

function entry(over: Partial<PublicBlogEntry> = {}): PublicBlogEntry {
    return {
        id: 7,
        publicUid: NEWS_UID,
        title: 'Neue Drehleiter eingeweiht',
        contentHtml: '<p>Seit Samstag</p><p>steht sie im Gerätehaus.</p>',
        authorName: 'Paula',
        publishedAt: '2026-09-12T10:00:00Z',
        attachments: [],
        contentMode: 'SIMPLE',
        rows: [],
        ...over,
    } as PublicBlogEntry
}

async function shown(newsUid: string | null) {
    const view = await mountSuspended(NewsTeaserCell, {
        props: {config: {newsUid}, stationUid: 'station-a', timezone: 'Europe/Berlin'},
    })
    await flushPromises()
    return view
}

/**
 * A news block shows the entry it names as the entry is now, and nothing about one the reader may
 * not see: only what the station has published on its public blog is ever drawn.
 */
describe('NewsTeaserCell', () => {
    beforeEach(() => {
        listPublicBlog.mockReset()
    })

    it('draws the entry it names and links to it on the public blog', async () => {
        listPublicBlog.mockResolvedValue([entry({publicUid: 'another', id: 3, title: 'Anderes'}), entry()])

        const view = await shown(NEWS_UID)

        expect(view.text()).toContain('Neue Drehleiter eingeweiht')
        expect(view.text()).toContain('Seit Samstag steht sie im Gerätehaus.')
        expect(view.text()).not.toContain('Anderes')
        expect(view.get('a').attributes('href')).toBe('/public/station/station-a/blog/7')
    })

    it('says the entry is not available where it is not on the public blog', async () => {
        listPublicBlog.mockResolvedValue([entry({publicUid: 'another'})])

        const view = await shown(NEWS_UID)

        expect(view.text()).toContain('Diese Neuigkeit ist hier nicht verfügbar.')
        expect(view.find('a').exists()).toBe(false)
    })

    it('does not call the entry gone while it is still looking', async () => {
        listPublicBlog.mockReturnValue(new Promise(() => {}))

        const view = await shown(NEWS_UID)

        expect(view.text()).toBe('')
    })

    it('asks for nothing where no entry is named', async () => {
        const view = await shown(null)

        expect(listPublicBlog).not.toHaveBeenCalled()
        expect(view.text()).toContain('Diese Neuigkeit ist hier nicht verfügbar.')
    })
})
