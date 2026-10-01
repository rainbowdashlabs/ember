/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {defineComponent, h} from 'vue'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import NewsTeaserCell from './NewsTeaserCell.vue'
import type {FoundNews} from '../embeddedNewsLookup'
import type {BlockAudience} from '@/api/generated/schema'
import {provideBlockAudience} from '@/composables/useBlockAudience'

const findEmbeddedNews = vi.fn()

vi.mock('../embeddedNewsLookup', () => ({
    findEmbeddedNews: (apiBase: string, stationUid: string, newsUid: string, audience: BlockAudience) =>
        findEmbeddedNews(apiBase, stationUid, newsUid, audience),
}))

function entry(newsUid: string, over: Partial<FoundNews> = {}): FoundNews {
    return {
        id: 7,
        publicUid: newsUid,
        title: 'Neue Drehleiter eingeweiht',
        summary: 'Seit Samstag steht sie im Gerätehaus.',
        publishedAt: '2026-09-12T10:00:00Z',
        source: {kind: 'PUBLIC', stationUid: 'station-a'},
        ...over,
    }
}

async function shown(newsUid: string | null, audience?: BlockAudience) {
    const props = {config: newsUid ? {newsUid} : {}, stationUid: 'station-a', timezone: 'Europe/Berlin'}
    const host = defineComponent({
        setup() {
            if (audience) provideBlockAudience(audience)
            return () => h(NewsTeaserCell, props)
        },
    })
    const view = await mountSuspended(host)
    await flushPromises()
    return view
}

/**
 * A news block shows the entry it names as the entry is now, looked up for the readers of its
 * content: on a public page from the public blog, in a news or wiki article for the station's
 * members. It calls the entry unavailable only when the lookup says there is none here.
 */
describe('NewsTeaserCell', () => {
    beforeEach(() => {
        findEmbeddedNews.mockReset()
    })

    it('looks the entry up for the public on a page and links to it on the public blog', async () => {
        const uid = '3f2b8c4e-1a6d-4e7f-9b0c-5d8e2f1a7c01'
        findEmbeddedNews.mockResolvedValue(entry(uid))

        const view = await shown(uid)

        expect(findEmbeddedNews).toHaveBeenCalledWith(expect.any(String), 'station-a', uid, 'PUBLIC')
        expect(view.text()).toContain('Neue Drehleiter eingeweiht')
        expect(view.text()).toContain('Seit Samstag steht sie im Gerätehaus.')
        expect(view.get('a').attributes('href')).toBe('/public/station/station-a/blog/7')
    })

    it('looks the entry up for the members in an article and links to it inside the station', async () => {
        const uid = '3f2b8c4e-1a6d-4e7f-9b0c-5d8e2f1a7c02'
        findEmbeddedNews.mockResolvedValue(entry(uid, {source: {kind: 'MEMBER'}}))

        const view = await shown(uid, 'MEMBERS')

        expect(findEmbeddedNews).toHaveBeenCalledWith(expect.any(String), 'station-a', uid, 'MEMBERS')
        expect(view.get('a').attributes('href')).not.toContain('/public/')
        expect(view.get('a').attributes('href')).toContain('7')
    })

    it('says the entry is not available where the lookup finds none', async () => {
        findEmbeddedNews.mockResolvedValue(null)

        const view = await shown('3f2b8c4e-1a6d-4e7f-9b0c-5d8e2f1a7c03')

        expect(view.text()).toContain('Diese Neuigkeit ist hier nicht verfügbar.')
        expect(view.find('a').exists()).toBe(false)
    })

    it('does not call the entry gone when reading it failed for another reason', async () => {
        findEmbeddedNews.mockRejectedValue({statusCode: 502})

        const view = await shown('3f2b8c4e-1a6d-4e7f-9b0c-5d8e2f1a7c04')

        expect(view.text()).toBe('')
    })

    it('does not call the entry gone while it is still looking', async () => {
        findEmbeddedNews.mockReturnValue(new Promise(() => {}))

        const view = await shown('3f2b8c4e-1a6d-4e7f-9b0c-5d8e2f1a7c05')

        expect(view.text()).toBe('')
    })

    it('asks for nothing where no entry is named', async () => {
        const view = await shown(null)

        expect(findEmbeddedNews).not.toHaveBeenCalled()
        expect(view.text()).toContain('Diese Neuigkeit ist hier nicht verfügbar.')
    })
})
