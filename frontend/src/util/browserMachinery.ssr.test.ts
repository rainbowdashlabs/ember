/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment node */
import {describe, expect, it} from 'vitest'
import {defineComponent, h, ref} from 'vue'
import {renderRequest} from '@/test/ssr'
import Modal from '@/components/feedback/Modal.vue'
import {outlineFor} from '@/util/glyphOutline'
import {bestSidebarMatch, followSidebarMatch} from '@/util/sidebarGroupState'

/** A sidebar group reduced to what it tells the register: how well it matches, and what is best. */
const MatchingGroup = defineComponent({
    props: {length: {type: Number, required: true}},
    setup(props) {
        followSidebarMatch(ref(props.length))
        return () => h('p', String(bestSidebarMatch()))
    },
})

/**
 * The browser state the public pages reach, rendered as two requests of one server process. Neither
 * render may write it, which the helper turns into a failure, and the second must draw what the first
 * did.
 */
describe('browser state on public pages', () => {
    it('claims no dialog layer for a dialog the page opens with', async () => {
        const page = () => h(Modal, {modelValue: true}, () => 'Inhalt')

        const first = await renderRequest(page)
        const second = await renderRequest(page)

        expect(second).toBe(first)
    })

    it('registers no sidebar group on the server', async () => {
        await renderRequest(() => [h(MatchingGroup, {length: 8}), h(MatchingGroup, {length: 3})])
        const second = await renderRequest(() => h(MatchingGroup, {length: 3}))

        expect(second).toBe('<p>0</p>')
    })

    it('outlines a glyph against paper without keeping what it worked out', async () => {
        const page = () => h('p', outlineFor('#fde68a', 'accent') ?? 'none')

        const first = await renderRequest(page)
        const second = await renderRequest(page)

        expect(first).toBe('<p>#1a1a1a</p>')
        expect(second).toBe(first)
    })
})
