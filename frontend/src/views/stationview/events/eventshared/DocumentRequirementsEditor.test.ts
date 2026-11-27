/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it, vi} from 'vitest'
import DocumentRequirementsEditor from './DocumentRequirementsEditor.vue'

vi.mock('@/composables/useSession', () => ({
    useSession: () => ({isModuleEnabled: () => true, hasPermission: () => true}),
}))

vi.mock('@/api', () => ({appointmentDocuments: {offeredTemplates: vi.fn()}}))

function editor(compact: boolean) {
    return mount(DocumentRequirementsEditor, {
        props: {modelValue: [], offeredCount: 1, compact},
        global: {stubs: {'font-awesome-icon': true, TemplatePickerModal: true, NuxtLink: true}},
    })
}

/**
 * Inside the appointment form the documents show what they are for behind an icon, and that they can be
 * signed in the app where documents are switched on.
 */
describe('DocumentRequirementsEditor', () => {
    it('keeps the explanation behind an icon that mentions signing in the app', () => {
        const wrapper = editor(true)

        expect(wrapper.get('[data-testid="hint-icon"]').attributes('aria-label')).toContain('in der App unterschreiben')
        expect(wrapper.findAll('p').some(paragraph => paragraph.text().includes('ausdrucken'))).toBe(false)
    })

    it('keeps its written explanation elsewhere', () => {
        expect(editor(false).text()).toContain('ausdrucken')
    })
})
