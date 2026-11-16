/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import RequiredTemplateRow from './RequiredTemplateRow.vue'
import {DocumentTemplateKind, type RequiredTemplate} from '@/api/generated/schema'

vi.mock('@/api/client', () => ({default: {get: () => Promise.reject(new Error('no picture in a test'))}}))

const i18n = createI18n({legacy: false, locale: 'de-DE', missingWarn: false, fallbackWarn: false, messages: {'de-DE': {}, en: {}}})

function required(fromEventGroup: boolean): RequiredTemplate {
  return {
    templateId: 4, name: 'Einverständnis', kind: DocumentTemplateKind.PDF, version: 1, archived: false,
    lastUsedAt: null, fromEventGroup,
  }
}

describe('RequiredTemplateRow', () => {
  it('lets a document of the appointment itself be taken off', async () => {
    const wrapper = mount(RequiredTemplateRow, {props: {template: required(false)}, global: {plugins: [i18n]}})

    expect(wrapper.find('[data-testid="document-requirement-from-group"]').exists()).toBe(false)
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('remove')).toHaveLength(1)
  })

  it('marks a document of the event group and offers no way to take it off', () => {
    const wrapper = mount(RequiredTemplateRow, {props: {template: required(true)}, global: {plugins: [i18n]}})

    expect(wrapper.find('[data-testid="document-requirement-from-group"]').exists()).toBe(true)
    expect(wrapper.find('button').exists()).toBe(false)
  })
})
