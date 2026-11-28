/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import {describe, expect, it} from 'vitest'
import type {RemotePiece} from '@/api/generated/schema'
import BareButton from '@/components/button/BareButton.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import PieceList from './PieceList.vue'

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    messages: {'de-DE': {lendingProduct: {freeInWindow: 'frei im Zeitraum', notFreeToday: 'heute nicht frei', freeToday: 'heute frei'}}, en: {}},
    missingWarn: false,
    fallbackWarn: false,
})

function piece(itemId: number, name: string, free = true, description = ''): RemotePiece {
    return {itemId, name, description, pictureKey: null, free}
}

const MANY = [
    piece(1, 'Siedler von Catan', true, 'Rohstoffe handeln'),
    piece(2, 'Carcassonne', false),
    ...[3, 4, 5, 6, 7].map(id => piece(id, `Spiel ${id}`)),
]

function list(pieces: RemotePiece[], windowed = false) {
    return mount(PieceList, {props: {pieces, stationUid: 'b1', windowed}, global: {plugins: [i18n]}})
}

/** The single pieces of a product, each with whether it is free, filtered and opened one by one. */
describe('PieceList', () => {
    it('shows whether a piece is free today or in the chosen days', () => {
        expect(list(MANY).text()).toContain('heute nicht frei')
        expect(list(MANY, true).text()).toContain('frei im Zeitraum')
    })

    it('filters a long list by name and description', async () => {
        const wrapper = list(MANY)

        await wrapper.findComponent(SearchInput).vm.$emit('update:modelValue', 'rohstoff')
        expect(wrapper.findAllComponents(BareButton)).toHaveLength(1)
    })

    it('offers no filter for a short list and opens a piece', async () => {
        const wrapper = list(MANY.slice(0, 2))

        expect(wrapper.findComponent(SearchInput).exists()).toBe(false)
        await wrapper.findAllComponents(BareButton)[0]!.trigger('click')
        expect(wrapper.emitted('open')?.[0]).toEqual([MANY[0]])
    })
})
