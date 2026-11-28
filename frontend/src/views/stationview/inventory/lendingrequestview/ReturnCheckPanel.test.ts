/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import {describe, expect, it, vi} from 'vitest'
import ReturnCheckPanel from './ReturnCheckPanel.vue'

const getReturnPieces = vi.fn()
const checkReturn = vi.fn()
vi.mock('@/api/lending', () => ({
    getReturnPieces: (id: number) => getReturnPieces(id),
    checkReturn: (id: number, pieces: unknown) => checkReturn(id, pieces),
}))

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}, missingWarn: false, fallbackWarn: false})

/** Every piece of the loan is answered for, in order unless marked otherwise, with a comment where not. */
describe('ReturnCheckPanel', () => {
    it('sends every piece with its state and the comment of a damaged one', async () => {
        getReturnPieces.mockResolvedValue([{itemId: 7, name: 'FuG 1', internalId: 'FG-7'}, {itemId: 8, name: 'FuG 2', internalId: null}])
        checkReturn.mockResolvedValue(undefined)
        const wrapper = mount(ReturnCheckPanel, {props: {requestId: 41}, global: {plugins: [i18n]}})
        await flushPromises()

        const pieces = wrapper.findAll('[data-testid="lending-return-piece"]')
        expect(pieces).toHaveLength(2)
        await pieces[1]!.get('[data-testid="lending-return-DAMAGED"]').trigger('click')
        await pieces[1]!.get('input').setValue('Antenne ab')
        await wrapper.get('[data-testid="lending-return-submit"]').trigger('click')
        await flushPromises()

        expect(checkReturn).toHaveBeenCalledWith(41, [
            {itemId: 7, state: 'OK', comment: null},
            {itemId: 8, state: 'DAMAGED', comment: 'Antenne ab'},
        ])
        expect(wrapper.emitted('done')).toHaveLength(1)
    })
})
