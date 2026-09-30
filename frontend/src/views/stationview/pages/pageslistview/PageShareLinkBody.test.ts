/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import PageShareLinkBody from './PageShareLinkBody.vue'

const CLOSED = 'Solange die öffentlichen Seiten der Wache ausgeschaltet sind, öffnet dieser Link nichts.'

function shown(link: {token: string | null; opens: boolean} | null) {
    return mount(PageShareLinkBody, {props: {link, busy: false, failure: null}})
}

/**
 * A link handed out while the station keeps its pages closed opens nothing, and the stranger holding
 * it has nobody to ask. The one who hands it out is told instead, before it is sent.
 *
 * @vitest-environment happy-dom
 */
describe('PageShareLinkBody', () => {
    it('says the link opens nothing while the station keeps its pages closed', () => {
        expect(shown({token: 'abc', opens: false}).text()).toContain(CLOSED)
    })

    it('says so before the first link is made as well', () => {
        expect(shown({token: null, opens: false}).text()).toContain(CLOSED)
    })

    it('says nothing of the kind while the pages are open', () => {
        expect(shown({token: 'abc', opens: true}).text()).not.toContain(CLOSED)
    })

    it('says nothing while the link is still being fetched', () => {
        expect(shown(null).text()).not.toContain(CLOSED)
    })
})
