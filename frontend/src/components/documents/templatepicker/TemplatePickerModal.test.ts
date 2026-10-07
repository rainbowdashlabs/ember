/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import type {DocumentTemplateSummary, TemplatePage} from '@/api/generated/schema'
import TemplatePickerModal from './TemplatePickerModal.vue'

function template(id: number, name: string): DocumentTemplateSummary {
    return {
        id, name, kind: 'LETTER', legal: false, forAppointments: false, selfService: false, ofAssociation: false,
        version: 1, createdAt: '2026-10-01T09:00:00Z', updatedAt: '2026-10-01T09:00:00Z', lastUsedAt: null,
        archivedAt: null,
    }
}

const FIRST: TemplatePage = {items: [template(1, 'Ausweis'), template(2, 'Bescheinigung')], total: 20, page: 0, size: 16}

const pages = vi.fn(async (): Promise<TemplatePage> => FIRST)

async function mountPicker(props: Record<string, unknown> = {}) {
    const picker = await mountSuspended(TemplatePickerModal, {
        props: {modelValue: true, pages, ...props},
        global: {stubs: {Modal: {template: '<div><slot/></div>'}, FileThumbnail: true}},
    })
    await flushPromises()
    return picker
}

function tiles(picker: Awaited<ReturnType<typeof mountPicker>>) {
    return picker.findAll('[data-testid="template-tile-choose"]')
}

/**
 * The picker asks the server for every page, search and order, so the order covers every template and
 * not only those on screen. One choice closes it; several are marked and taken together, and what was
 * chosen before cannot be chosen again.
 */
describe('TemplatePickerModal', () => {
    beforeEach(() => {
        pages.mockClear()
        vi.useFakeTimers({shouldAdvanceTime: true})
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it('asks for the first page, most recently used first, with what the screen fixes', async () => {
        await mountPicker({fixed: {forAppointments: false}})

        expect(pages).toHaveBeenCalledWith(expect.objectContaining({
            forAppointments: false, sort: 'LAST_USED', page: 0, size: 16,
        }))
    })

    it('asks the server again from the first page when the order changes', async () => {
        const picker = await mountPicker()
        await picker.find('select[data-testid="template-sort"]').setValue('NAME')
        await flushPromises()

        expect(pages).toHaveBeenLastCalledWith(expect.objectContaining({sort: 'NAME', page: 0}))
    })

    it('asks for the next page from the server', async () => {
        const picker = await mountPicker()
        await picker.find('button[aria-label="Weiter"]').trigger('click')
        await flushPromises()

        expect(pages).toHaveBeenLastCalledWith(expect.objectContaining({page: 1}))
    })

    it('searches once the typing settles', async () => {
        const picker = await mountPicker()
        await picker.find('[data-testid="template-search"] input').setValue('aus')
        await vi.advanceTimersByTimeAsync(400)
        await flushPromises()

        expect(pages).toHaveBeenLastCalledWith(expect.objectContaining({q: 'aus', page: 0}))
    })

    it('takes one template and closes', async () => {
        const picker = await mountPicker()
        await tiles(picker)[1]!.trigger('click')

        expect(picker.emitted('pick')).toEqual([[[FIRST.items[1]]]])
        expect(picker.emitted('update:modelValue')).toEqual([[false]])
    })

    it('marks several and takes them together, but not one chosen before', async () => {
        const picker = await mountPicker({multiple: true, chosenIds: [1]})

        expect(tiles(picker)[0]!.attributes('disabled')).toBeDefined()
        await tiles(picker)[1]!.trigger('click')
        await picker.find('[data-testid="template-picker-take"]').trigger('click')

        expect(picker.emitted('pick')).toEqual([[[FIRST.items[1]]]])
    })
})
