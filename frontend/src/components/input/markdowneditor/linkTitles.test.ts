/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {Editor} from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import {internalLinkTitle, InternalLinkTitles, type LinkTitleContext} from './linkTitles'

vi.mock('@/api', () => ({
    news: {getNews: async (id: number) => {
        if (id === 404) throw new Error('not yours')
        return {title: 'Dienstplan Oktober'}
    }},
    events: {
        getEvent: async () => ({name: 'Übungsabend'}),
        getFederatedEvent: async (stationUid: string) => ({event: {name: `Sommerfest bei ${stationUid}`}}),
    },
    knowledgeBase: {getFile: async () => ({file: {name: 'Atemschutz'}})},
}))
vi.mock('@/api/pageManage', () => ({getPage: async () => ({title: 'Über uns'})}))

const ORIGIN = 'https://ember.example.com'

const context: LinkTitleContext = {
    routeOf(path): {name: string; params: Record<string, string>} | null {
        const news = /^\/station\/news\/(\d+)/.exec(path)
        if (news) return {name: 'news-detail', params: {id: news[1]!}}
        const partner = /^\/station\/federation\/events\/([\w-]+)\/(\d+)/.exec(path)
        if (partner) return {name: 'federated-event-detail', params: {stationUid: partner[1]!, eventId: partner[2]!}}
        if (path.startsWith('/station/events/upcoming')) return {name: 'events-upcoming', params: {}}
        return null
    },
    pageTitle: name => ({
        'events-upcoming': 'Anstehende Termine',
        'federated-event-detail': 'Termin einer Partnerwache',
    } as Record<string, string>)[name] ?? null,
}

/** A pasted address of this app reads as the title of what it opens, as far as the reader may see it. */
describe('internalLinkTitle', () => {
    it('names a news entry by its title', async () => {
        expect(await internalLinkTitle(`${ORIGIN}/station/news/42?station=abc`, ORIGIN, context)).toBe('Dienstplan Oktober')
    })

    it('names an appointment of a partner station by its own name, not by the page title', async () => {
        expect(await internalLinkTitle(`${ORIGIN}/station/federation/events/nord/7`, ORIGIN, context))
            .toBe('Sommerfest bei nord')
    })

    it('names a page without an item by the page title', async () => {
        expect(await internalLinkTitle(`${ORIGIN}/station/events/upcoming`, ORIGIN, context)).toBe('Anstehende Termine')
    })

    it('leaves an entry the reader may not read unnamed', async () => {
        expect(await internalLinkTitle(`${ORIGIN}/station/news/404`, ORIGIN, context)).toBeNull()
    })

    it('leaves another site and an unknown address alone', async () => {
        expect(await internalLinkTitle('https://example.org/station/news/42', ORIGIN, context)).toBeNull()
        expect(await internalLinkTitle(`${ORIGIN}/nowhere`, ORIGIN, context)).toBeNull()
        expect(await internalLinkTitle('kein link', ORIGIN, context)).toBeNull()
    })
})

describe('InternalLinkTitles', () => {
    let editor: Editor | null = null

    afterEach(() => {
        editor?.destroy()
        editor = null
    })

    function paste(text: string) {
        const event = new Event('paste') as ClipboardEvent
        Object.defineProperty(event, 'clipboardData', {value: {getData: () => text}})
        editor!.view.someProp('handlePaste', handle => handle(editor!.view, event, editor!.view.state.selection.content()))
    }

    it('puts the pasted address in as a link and then names it by its title', async () => {
        editor = new Editor({
            extensions: [StarterKit, Link, InternalLinkTitles.configure({resolve: async () => 'Dienstplan Oktober'})],
            content: '<p>Siehe </p>',
        })
        editor.commands.setTextSelection(6)

        paste(`${ORIGIN}/station/news/42`)
        await vi.waitFor(() => expect(editor!.getText()).toContain('Dienstplan Oktober'))

        expect(editor.getHTML()).toContain(`<a target="_blank" rel="noopener noreferrer nofollow" href="${ORIGIN}/station/news/42">Dienstplan Oktober</a>`)
    })

    it('keeps the address where no title is found', async () => {
        editor = new Editor({
            extensions: [StarterKit, Link, InternalLinkTitles.configure({resolve: async () => null})],
            content: '<p>Siehe </p>',
        })
        editor.commands.setTextSelection(6)

        paste('https://example.org/x')
        await Promise.resolve()

        expect(editor.getText()).toContain('https://example.org/x')
    })
})
