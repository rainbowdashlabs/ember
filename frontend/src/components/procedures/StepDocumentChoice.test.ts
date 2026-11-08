/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import type {StepDocument} from '@/api/generated/schema'
import StepDocumentChoice from './StepDocumentChoice.vue'
import {stepDocumentOf, stepKindOf} from './stepDocument'

let generatesDocuments = true

vi.mock('@/composables/useSession', () => ({
    useSession: () => ({hasPermission: () => generatesDocuments}),
}))

const PICKER = {
    props: ['modelValue'],
    emits: ['update:modelValue', 'pick'],
    template: '<div v-if="modelValue" data-testid="picker"><button data-testid="pick" @click="$emit(\'pick\', [{id: 7, name: \'Fotoerlaubnis\'}])"/>'
        + '<button data-testid="close" @click="$emit(\'update:modelValue\', false)"/></div>',
}

async function mountChoice(document: StepDocument | null, locked = false) {
    const changes: (StepDocument | null)[] = []
    const view = await mountSuspended(StepDocumentChoice, {
        props: {modelValue: document, locked, 'onUpdate:modelValue': (next: StepDocument | null) => changes.push(next)},
        global: {stubs: {TemplatePickerModal: PICKER, FileThumbnail: true}},
    })
    return {view, changes}
}

/** A step asks to sign a document picked from the templates, or is ticked by hand. */
describe('StepDocumentChoice', () => {
    beforeEach(() => {
        generatesDocuments = true
    })

    it('opens the picker when switched on and takes the template picked', async () => {
        const {view, changes} = await mountChoice(null)

        await view.find('[data-testid="step-document-toggle"]').trigger('click')
        await flushPromises()
        await view.find('[data-testid="pick"]').trigger('click')

        expect(changes).toEqual([{id: 7, name: 'Fotoerlaubnis'}])
    })

    it('stays a step ticked by hand when the picker closes without a choice', async () => {
        const {view, changes} = await mountChoice(null)

        await view.find('[data-testid="step-document-toggle"]').trigger('click')
        await flushPromises()
        await view.find('[data-testid="close"]').trigger('click')
        await flushPromises()

        expect(changes).toEqual([])
        expect(view.find('[data-testid="step-document-open"]').exists()).toBe(false)
    })

    it('shows a document already made without a way to change it', async () => {
        const {view} = await mountChoice({id: 7, name: 'Fotoerlaubnis'}, true)

        expect(view.find('[data-testid="step-document-name"]').text()).toBe('Fotoerlaubnis')
        expect(view.find('[data-testid="step-document-open"]').exists()).toBe(false)
    })

    it('says so where the document of a step is gone', async () => {
        const {view} = await mountChoice({id: 0, name: ''})

        expect(view.find('[data-testid="step-document-gone"]').exists()).toBe(true)
        expect(view.find('[data-testid="step-document-name"]').exists()).toBe(false)
    })

    it('lets somebody who generates no documents only switch a document step off', async () => {
        generatesDocuments = false
        const {view: none} = await mountChoice(null)
        expect(none.find('[data-testid="step-document"]').exists()).toBe(false)

        const {view, changes} = await mountChoice({id: 7, name: 'Fotoerlaubnis'})
        expect(view.find('[data-testid="step-document-open"]').exists()).toBe(false)
        await view.find('[data-testid="step-document-toggle"]').trigger('click')

        expect(changes).toEqual([null])
    })
})

describe('stepDocument', () => {
    it('names a step by the documents of its page and sends its kind', () => {
        const documents = [{id: 7, name: 'Fotoerlaubnis'}]

        expect(stepDocumentOf({kind: 'DOCUMENT', documentTemplateId: 7}, documents)).toEqual({id: 7, name: 'Fotoerlaubnis'})
        expect(stepDocumentOf({kind: 'DOCUMENT', documentTemplateId: null}, documents)).toEqual({id: 0, name: ''})
        expect(stepDocumentOf({kind: 'CHECK', documentTemplateId: null}, documents)).toBeNull()
        expect(stepKindOf({id: 7, name: 'Fotoerlaubnis'})).toEqual({kind: 'DOCUMENT', documentTemplateId: 7})
        expect(stepKindOf(null)).toEqual({kind: 'CHECK', documentTemplateId: null})
    })
})
