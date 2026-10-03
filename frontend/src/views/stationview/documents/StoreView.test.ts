/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, type VueWrapper} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import DocumentModal from '@/components/documents/DocumentModal.vue'
import StoreView from './StoreView.vue'

const listStation = vi.fn()

vi.mock('@/api', () => ({
    documents: {
        listStation: (...args: unknown[]) => listStation(...args),
        listTags: vi.fn(async () => []),
        listIds: vi.fn(async () => []),
        prune: vi.fn(),
        uploadForStation: vi.fn(),
    },
    stationMembers: {listMembers: vi.fn(async () => [])},
    documentTemplates: {listJobs: vi.fn(async () => [])},
}))

const permissions = {held: true}

vi.mock('@/composables/usePermissions', () => ({
    usePermissions: () => ({hasPermission: () => permissions.held}),
}))

function documentTitled(id: number, title: string) {
    return {
        id, title, mimeType: 'text/plain', createdAt: '2026-10-01T08:00:00Z', sizeBytes: 9, tags: [],
        hidden: false, keepOnArchive: false, departedNames: [], hasThumbnail: false,
    }
}

function boxes(view: VueWrapper) {
    return view.findAll<HTMLInputElement>('[data-testid="document-select"]')
}

/**
 * Choosing documents on the station's document store while a search narrows it.
 *
 * <p>The list stays on screen until the narrowed one arrives. A document ticked on it in that moment
 * was dropped when the search went out or, when the answer was slower, stayed chosen while the new
 * list no longer showed it, and was then deleted unseen.
 */
describe('StoreView', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        permissions.held = true
        listStation.mockResolvedValueOnce({documents: [documentTitled(1, 'Anderes'), documentTitled(2, 'Altlast')], total: 2})
    })

    it('lets nothing be chosen on a list a search is about to replace', async () => {
        const view = await mountSuspended(StoreView)
        await flushPromises()
        expect(boxes(view).every(box => !box.element.disabled)).toBe(true)

        let answer: (page: unknown) => void = () => {}
        listStation.mockReturnValueOnce(new Promise(resolve => { answer = resolve }))
        await view.find('input[placeholder="Titel oder Inhalt"]').setValue('Altlast')

        expect(boxes(view).every(box => box.element.disabled)).toBe(true)

        await vi.waitFor(() => expect(listStation).toHaveBeenCalledTimes(2))
        expect(boxes(view).every(box => box.element.disabled)).toBe(true)

        answer({documents: [documentTitled(2, 'Altlast')], total: 1})
        await flushPromises()

        expect(boxes(view)).toHaveLength(1)
        expect(boxes(view)[0]!.element.disabled).toBe(false)
    })
})

const generateDialog = {name: 'GenerateDocumentModal', template: '<div data-testid="generate-stub"/>', emits: ['filed']}

async function mountStore() {
    const view = await mountSuspended(StoreView, {global: {stubs: {GenerateDocumentModal: generateDialog}}})
    await flushPromises()
    return view
}

/**
 * Generating from the store, for one member or for many: offered to whoever may file documents for
 * members, and a single document generated is shown in the store straight away, opened on the first page.
 */
describe('StoreView generating', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        permissions.held = true
        listStation.mockResolvedValue({documents: [documentTitled(1, 'Anderes')], total: 1})
    })

    it('offers generating to whoever may file documents for members', async () => {
        const view = await mountStore()

        expect(view.find('[data-testid="store-generate"]').exists()).toBe(true)
        expect(view.find('[data-testid="store-bulk"]').exists()).toBe(true)
    })

    it('offers no generating without the right', async () => {
        permissions.held = false
        const view = await mountStore()

        expect(view.find('[data-testid="store-generate"]').exists()).toBe(false)
        expect(view.find('[data-testid="store-bulk"]').exists()).toBe(false)
    })

    it('fetches the list again and opens the document generated', async () => {
        const view = await mountStore()
        await view.find('[data-testid="store-generate"]').trigger('click')

        listStation.mockResolvedValueOnce({documents: [documentTitled(5, 'Ausweis'), documentTitled(1, 'Anderes')], total: 2})
        view.findComponent(generateDialog).vm.$emit('filed', 5)
        await flushPromises()

        expect(listStation).toHaveBeenCalledTimes(2)
        const dialog = view.findComponent(DocumentModal)
        expect(dialog.props('modelValue')).toBe(true)
        expect(dialog.props('document')).toMatchObject({id: 5, title: 'Ausweis'})
    })
})
