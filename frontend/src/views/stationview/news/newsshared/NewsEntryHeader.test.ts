/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import NewsEntryHeader from './NewsEntryHeader.vue'
import type {NewsEntry} from '@/api/news'

function entry(overrides: Partial<NewsEntry> = {}): NewsEntry {
    return {
        id: 5,
        title: 'Ember 26.17.1',
        contentMarkdown: '',
        contentHtml: '<p>x</p>',
        contentMode: 'MARKDOWN',
        authorName: 'Ember',
        author: null,
        publishedAt: '2026-09-15T19:00:00Z',
        restricted: false,
        systemEntry: false,
        ...overrides,
    } as NewsEntry
}

/**
 * Who an entry is shown as being from, and what may be done to it.
 *
 * <p>An entry the instance wrote belongs to no station, so every action a station has over its own
 * entries is refused on one: editing it, deleting it and asking who has read it all answer "not
 * found". Offering the buttons anyway is offering three ways to be told no.
 */
describe('NewsEntryHeader', () => {
    function header(news: NewsEntry, canManage = true) {
        return mount(NewsEntryHeader, {
            props: {entry: news, canManage},
            slots: {actions: '<button data-testid="an-action">tu etwas</button>'},
        })
    }

    it('offers a manager the actions on their station\'s own entry', () => {
        const view = header(entry())

        expect(view.find('[data-testid="an-action"]').exists()).toBe(true)
        expect(view.find('[data-testid="system-entry-logo"]').exists()).toBe(false)
    })

    it('offers nothing on an entry the instance wrote', () => {
        const view = header(entry({systemEntry: true}))

        expect(view.find('[data-testid="an-action"]').exists()).toBe(false)
    })

    /** The instance is nobody's member, so initials of a product name stand in for nothing. */
    it('shows the Ember logo where an entry came from the instance', () => {
        const view = header(entry({systemEntry: true}))

        expect(view.find('[data-testid="system-entry-logo"]').exists()).toBe(true)
    })

    it('shows a member who is not managing nothing to manage with', () => {
        const view = header(entry(), false)

        expect(view.find('[data-testid="an-action"]').exists()).toBe(false)
    })
})
