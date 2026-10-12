/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {Extension} from '@tiptap/vue-3'
import {Plugin, PluginKey} from '@tiptap/pm/state'
import type {EditorView} from '@tiptap/pm/view'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {events, knowledgeBase, news} from '@/api'
import {getPage} from '@/api/pageManage'

/** The parameters of the address a page was opened by, as its route names them. */
type RouteParams = Readonly<Record<string, string>>

/** How the title of one kind of page is read from the parameters of its address, or null where they do not fit. */
type TitleOf = (params: RouteParams) => Promise<string> | null

function idOf(params: RouteParams, name: string): number | null {
    const id = Number(params[name])
    return Number.isInteger(id) && id > 0 ? id : null
}

/** The title of a thing named by one id parameter. */
function byId(name: string, read: (id: number) => Promise<string>): TitleOf {
    return params => {
        const id = idOf(params, name)
        return id === null ? null : read(id)
    }
}

/** The title of a partner station's thing, named by the station and one id parameter. */
function byPartner(name: string, read: (stationUid: string, id: number) => Promise<string>): TitleOf {
    return params => {
        const id = idOf(params, name)
        const stationUid = params.stationUid
        return id === null || !stationUid ? null : read(stationUid, id)
    }
}

/**
 * The pages whose address names one thing with a title of its own, by route name, the station's own
 * and those of a partner station alike. Each is read with the reader's own access, so a title the
 * reader may not see stays unknown and the link keeps the page's name or its address.
 */
const ITEM_TITLES: Readonly<Record<string, TitleOf>> = {
    'news-detail': byId('id', async id => (await news.getNews(id)).title),
    'event-detail': byId('id', async id => (await events.getEvent(id)).name),
    'event-detail-date': byId('id', async id => (await events.getEvent(id)).name),
    'kb-file': byId('id', async id => (await knowledgeBase.getFile(id)).file.name),
    'page-editor': byId('id', async id => (await getPage(id)).title),
    'federated-event-detail': byPartner('eventId', async (uid, id) => (await events.getFederatedEvent(uid, id)).event.name),
    'federated-news-detail': byPartner('newsId', async (uid, id) => (await news.getFederatedNews(uid, id)).title),
    'federated-kb-file': byPartner('fileId', async (uid, id) => (await knowledgeBase.getFederatedFile(uid, id)).name),
}

/** What a resolver needs of the app: where an address leads, and the name of a page. */
export interface LinkTitleContext {
    /** The route an address of this app leads to, by name and parameters, or null where it leads nowhere known. */
    routeOf: (path: string) => {name: string; params: RouteParams} | null
    /** The title a page goes by in the app, or null where it has none. */
    pageTitle: (routeName: string) => string | null
}

/**
 * The probable title of an address inside this app: the title of the news entry, appointment, wiki
 * article or page it opens, read as the reader sees it, else the name of the page it opens. An address
 * of another site, or one leading nowhere known, has none.
 *
 * @param href    the pasted address
 * @param origin  the origin this app is served from
 * @param context how the app's routes and page names are read
 */
export async function internalLinkTitle(href: string, origin: string, context: LinkTitleContext): Promise<string | null> {
    let url: URL
    try {
        url = new URL(href)
    } catch {
        return null
    }
    if (url.origin !== origin) return null
    const route = context.routeOf(url.pathname + url.search)
    if (!route) return null
    const reading = ITEM_TITLES[route.name]?.(route.params) ?? null
    const title = reading ? await reading.catch(() => null) : null
    if (title?.trim()) return title.trim()
    return context.pageTitle(route.name)
}

/**
 * The resolver of an editor, reading the app's routes from its router and the names of its pages from
 * the page titles every page carries. Called while the editor sets up.
 *
 * @return what finds the title of a pasted address
 */
export function useLinkTitleResolver(): (href: string) => Promise<string | null> {
    const router = useRouter()
    const {t, te} = useI18n()
    const context: LinkTitleContext = {
        routeOf(path) {
            const resolved = router.resolve(path)
            if (resolved.matched.length === 0 || typeof resolved.name !== 'string') return null
            const params = Object.fromEntries(Object.entries(resolved.params)
                .map(([key, value]) => [key, Array.isArray(value) ? value.join('/') : String(value)]))
            return {name: resolved.name, params}
        },
        pageTitle(routeName) {
            const key = `pages.${routeName}.title`
            return te(key) ? t(key) : null
        },
    }
    return href => internalLinkTitle(href, window.location.origin, context)
}

const ADDRESS = /^https?:\/\/\S+$/

/**
 * Names a pasted address of this app by its title. The address goes in as a link at once, and the
 * title takes the place of its words as soon as it is known, as a step of its own, so undoing it
 * brings the address back. Where the words were changed in the meantime, or no title is found, the
 * address stays as pasted. A paste over selected words is left to the link extension, which links the
 * words.
 */
export const InternalLinkTitles = Extension.create<{resolve: (href: string) => Promise<string | null>}>({
    name: 'internalLinkTitles',

    addOptions() {
        return {resolve: async () => null}
    },

    addProseMirrorPlugins() {
        const resolve = this.options.resolve
        return [new Plugin({
            key: new PluginKey('internalLinkTitles'),
            props: {
                handlePaste: (view, event) => {
                    const href = event.clipboardData?.getData('text/plain').trim() ?? ''
                    if (!ADDRESS.test(href) || !view.state.selection.empty) return false
                    const link = view.state.schema.marks.link
                    if (!link) return false
                    const from = view.state.selection.from
                    view.dispatch(view.state.tr.insert(from, view.state.schema.text(href, [link.create({href})])))
                    void resolve(href).then(title => {
                        if (title) nameLink(view, from, href, title)
                    })
                    return true
                },
            },
        })]
    },
})

function nameLink(view: EditorView, from: number, href: string, title: string) {
    if (view.isDestroyed) return
    const {state} = view
    const to = from + href.length
    if (to > state.doc.content.size || state.doc.textBetween(from, to) !== href) return
    const link = state.schema.marks.link
    if (!link || !state.doc.rangeHasMark(from, to, link)) return
    view.dispatch(state.tr.replaceWith(from, to, state.schema.text(title, [link.create({href})])))
}
