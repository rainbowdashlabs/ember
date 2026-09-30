/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import NewsSearchPicker from './NewsSearchPicker.vue'
import type {NewsSearchResult} from '@/api/news'

const searchNews = vi.fn()
const getPublicNewsTeaser = vi.fn()

vi.mock('@/api/news', () => ({
    searchNews: (query: string, limit: number) => searchNews(query, limit),
    getPublicNewsTeaser: (apiBase: string, stationUid: string, newsUid: string) =>
        getPublicNewsTeaser(apiBase, stationUid, newsUid),
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

/**
 * The news block picker asks the server for the station's public entries by title, a few at a time,
 * and offers more where the server says there are more.
 */
describe('NewsSearchPicker', () => {
    beforeEach(() => {
        searchNews.mockReset()
        getPublicNewsTeaser.mockReset()
    })

    it('searches the server for what is typed, once the typing pauses', async () => {
        searchNews.mockImplementation((query: string) =>
            Promise.resolve({entries: query ? entries(1, 9) : entries(2), more: false}))
        const picker = await mountSuspended(NewsSearchPicker, {props: {modelValue: null, stationUid: 'station-a'}})

        await picker.get('input').trigger('focusin')
        await vi.waitUntil(() => options(picker).length === 2)
        expect(searchNews).toHaveBeenLastCalledWith('', 5)

        await picker.get('input').setValue('Drehl')
        await picker.get('input').setValue('Drehleiter')
        await vi.waitUntil(() => options(picker).length === 1)

        expect(searchNews).toHaveBeenLastCalledWith('Drehleiter', 5)
        expect(searchNews.mock.calls.filter(([query]) => query === 'Drehl')).toHaveLength(0)
        expect(options(picker)[0]).toContain('Drehleiter 9')
    })

    it('offers more where the server has more, and asks again with room for them', async () => {
        searchNews.mockImplementation((_query: string, limit: number) =>
            Promise.resolve({entries: entries(Math.min(limit, 7)), more: limit < 7}))
        const picker = await mountSuspended(NewsSearchPicker, {props: {modelValue: null, stationUid: 'station-a'}})

        await picker.get('input').trigger('focusin')
        await vi.waitUntil(() => options(picker).length === 5)

        await picker.get('[data-testid="news-search-more"]').trigger('click')
        await vi.waitUntil(() => options(picker).length === 7)

        expect(searchNews).toHaveBeenLastCalledWith('', 10)
        expect(picker.find('[data-testid="news-search-more"]').exists()).toBe(false)
    })

    it('shows the title of the entry already chosen, read by its id', async () => {
        getPublicNewsTeaser.mockResolvedValue({id: 3, publicUid: 'uid-3', title: 'Neue Drehleiter', summary: '', publishedAt: null})

        const picker = await mountSuspended(NewsSearchPicker, {props: {modelValue: 'uid-3', stationUid: 'station-a'}})
        await flushPromises()

        expect(getPublicNewsTeaser).toHaveBeenCalledWith(expect.any(String), 'station-a', 'uid-3')
        expect(picker.text()).toContain('Neue Drehleiter')
    })
})
