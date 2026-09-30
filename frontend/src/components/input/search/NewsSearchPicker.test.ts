/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {defineComponent, h} from 'vue'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import NewsSearchPicker from './NewsSearchPicker.vue'
import type {NewsSearchResult} from '@/api/news'
import type {BlockAudience} from '@/api/pageManage'
import {provideBlockAudience} from '@/composables/useBlockAudience'

const searchNews = vi.fn()
const findEmbeddedNews = vi.fn()

vi.mock('@/api/news', () => ({
    searchNews: (query: string, limit: number, scope: BlockAudience) => searchNews(query, limit, scope),
}))

vi.mock('@/components/content/blockeditor/embeddedNewsLookup', () => ({
    findEmbeddedNews: (apiBase: string, stationUid: string, newsUid: string, audience: BlockAudience) =>
        findEmbeddedNews(apiBase, stationUid, newsUid, audience),
}))

function entries(count: number, from = 1): NewsSearchResult[] {
    return Array.from({length: count}, (_, index) => ({
        publicUid: `uid-${from + index}`,
        title: `Drehleiter ${from + index}`,
        summary: '',
        publishedAt: null,
    }))
}

function options(picker: {findAll(selector: string): {text(): string}[]}): string[] {
    return picker.findAll('[role="option"]').map(option => option.text())
}

async function picker(modelValue: string | null, audience?: BlockAudience) {
    const host = defineComponent({
        setup() {
            if (audience) provideBlockAudience(audience)
            return () => h(NewsSearchPicker, {modelValue, stationUid: 'station-a'})
        },
    })
    return mountSuspended(host)
}

/**
 * The news block picker asks the server for the station's entries by title, a few at a time, offers
 * more where the server says there are more, and offers what the block's readers may all read: on a
 * page the public blog, in an article every entry every member may read.
 */
describe('NewsSearchPicker', () => {
    beforeEach(() => {
        searchNews.mockReset()
        findEmbeddedNews.mockReset()
    })

    it('searches the public blog on a page, once the typing pauses, and says so', async () => {
        searchNews.mockImplementation((query: string) =>
            Promise.resolve({entries: query ? entries(1, 9) : entries(2), more: false}))
        const view = await picker(null)

        await view.get('input').trigger('focusin')
        await vi.waitUntil(() => options(view).length === 2)
        expect(searchNews).toHaveBeenLastCalledWith('', 5, 'PUBLIC')

        await view.get('input').setValue('Drehl')
        await view.get('input').setValue('Drehleiter')
        await vi.waitUntil(() => options(view).length === 1)

        expect(searchNews).toHaveBeenLastCalledWith('Drehleiter', 5, 'PUBLIC')
        expect(searchNews.mock.calls.filter(([query]) => query === 'Drehl')).toHaveLength(0)
        expect(options(view)[0]).toContain('Drehleiter 9')
        expect(view.get('[data-testid="news-picker-hint"]').text()).toContain('öffentlichen Blog')
    })

    it('searches every entry every member may read in an article, and says so', async () => {
        searchNews.mockResolvedValue({entries: entries(1), more: false})
        const view = await picker(null, 'MEMBERS')

        await view.get('input').trigger('focusin')
        await vi.waitUntil(() => options(view).length === 1)

        expect(searchNews).toHaveBeenLastCalledWith('', 5, 'MEMBERS')
        expect(view.get('[data-testid="news-picker-hint"]').text()).toContain('alle Mitglieder')
    })

    it('offers more where the server has more, and asks again with room for them', async () => {
        searchNews.mockImplementation((_query: string, limit: number) =>
            Promise.resolve({entries: entries(Math.min(limit, 7)), more: limit < 7}))
        const view = await picker(null)

        await view.get('input').trigger('focusin')
        await vi.waitUntil(() => options(view).length === 5)

        await view.get('[data-testid="news-search-more"]').trigger('click')
        await vi.waitUntil(() => options(view).length === 7)

        expect(searchNews).toHaveBeenLastCalledWith('', 10, 'PUBLIC')
        expect(view.find('[data-testid="news-search-more"]').exists()).toBe(false)
    })

    it('shows the title of the entry already chosen, looked up for the same readers', async () => {
        findEmbeddedNews.mockResolvedValue({id: 3, publicUid: 'uid-3', title: 'Neue Drehleiter', summary: '', publishedAt: null})

        const view = await picker('uid-3', 'MEMBERS')
        await flushPromises()

        expect(findEmbeddedNews).toHaveBeenCalledWith(expect.any(String), 'station-a', 'uid-3', 'MEMBERS')
        expect(view.text()).toContain('Neue Drehleiter')
    })
})
