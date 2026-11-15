/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import RegistrationFieldInputs from './RegistrationFieldInputs.vue'
import type {EventRegistrationField} from '@/api/generated/schema'

const i18n = createI18n({
  legacy: false,
  locale: 'de-DE',
  missingWarn: false,
  fallbackWarn: false,
  messages: {
    'de-DE': {events: {registrationFields: {range: 'Von {min} bis {max}', missing: 'Es fehlt: {fields}'}}},
    en: {},
  },
})

function question(id: number, name: string, fieldType: string, config: Record<string, unknown> = {}): EventRegistrationField {
  return {id, eventId: null, eventGroupId: 70, name, fieldType, config, position: id, overview: false} as EventRegistrationField
}

function mountInputs(fields: EventRegistrationField[], answers: Record<number, string>) {
  return mount(RegistrationFieldInputs, {
    props: {fields, modelValue: answers, testIdPrefix: 'question'},
    global: {plugins: [i18n]},
  })
}

describe('RegistrationFieldInputs', () => {
  it('marks a required question and names it while it is open', () => {
    const wrapper = mountInputs([question(5, 'T-Shirt', 'TEXT', {required: true})], {5: ''})

    expect(wrapper.find('.text-error').exists()).toBe(true)
    expect(wrapper.text()).toContain('Es fehlt: T-Shirt')
  })

  it('shows the bounds below a number question', () => {
    const wrapper = mountInputs([question(7, 'Alter', 'NUMBER', {min: 10, max: 18})], {7: ''})

    expect(wrapper.text()).toContain('Von 10 bis 18')
  })

  it('hands a typed answer back with the others kept', async () => {
    const wrapper = mountInputs([question(5, 'T-Shirt', 'TEXT'), question(6, 'Allergien', 'TEXT')], {5: '', 6: 'keine'})

    await wrapper.find('[data-testid="question-5"] input, input[data-testid="question-5"]').setValue('M')

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([{5: 'M', 6: 'keine'}])
  })
})
