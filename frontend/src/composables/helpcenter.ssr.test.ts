/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment node */
import {describe, expect, it} from 'vitest'
import {defineComponent, h} from 'vue'
import {useI18n} from 'vue-i18n'
import {renderRequest} from '@/test/ssr'
import {loadHelpcenterMessages} from './useHelpcenterMessages'
import {useHelpSearch} from './useHelpSearch'

/**
 * A help page as its layout draws it: the help text merged into the request's own i18n instance
 * first, then one line of it. The instance is handed to Nuxt the way the i18n module hands it over.
 */
const HelpPage = defineComponent({
    async setup() {
        const {t} = useI18n()
        useNuxtApp().provide('i18n', useI18n({useScope: 'global'}))
        await loadHelpcenterMessages()
        return () => h('p', t('helpCenter.stationMoved.title'))
    },
})

/** The search box of the help centre reduced to what it answers. */
const SearchBox = defineComponent({
    setup() {
        const {query, results} = useHelpSearch()
        query.value = 'Wache'
        return () => h('p', String(results.value.length))
    },
})

/**
 * The help centre rendered as two requests of one server process. Every request has an i18n
 * instance of its own, and each of them needs the help text; the search index is the browser's
 * business and is neither built nor written on the server.
 */
describe('help centre on the server', () => {
    it('gives every request the help text', async () => {
        const page = () => h(HelpPage)

        expect(await renderRequest(page)).toBe('<p>Wache verschoben</p>')
        expect(await renderRequest(page)).toBe('<p>Wache verschoben</p>')
    })

    it('builds no search index', async () => {
        const page = () => h(SearchBox)

        expect(await renderRequest(page)).toBe('<p>0</p>')
        expect(await renderRequest(page)).toBe('<p>0</p>')
    })
})
