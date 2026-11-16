/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import InheritedFieldsCard from './InheritedFieldsCard.vue'
import type {AppointmentField} from '@/api/generated/schema'

function field(id: number, name: string, value: string): AppointmentField {
  return {
    id, eventId: 7, name, fieldType: 'TEXT', value, position: 0, overview: false, attendanceFieldId: null,
    isPublic: true, selfRegistrationState: null,
    config: {selfRegistration: false, perDate: false, required: false, managersOnly: false},
  } as AppointmentField
}

describe('InheritedFieldsCard', () => {
  it('offers to override a group field and marks one the appointment already replaces', async () => {
    const wrapper = mount(InheritedFieldsCard, {
      props: {
        inherited: [field(-1, 'Ort', 'Gerätehaus'), field(-2, 'Mitbringen', 'Schlafsack')],
        own: [{name: 'ort', fieldType: 'TEXT', config: null, value: 'Zeltplatz', overview: false, attendanceFieldId: null, isPublic: true}],
      },
    })

    const rows = wrapper.findAll('[data-testid="inherited-field"]')
    expect(rows).toHaveLength(2)
    expect(rows[0]!.find('[data-testid="override-field"]').exists()).toBe(false)
    await rows[1]!.find('[data-testid="override-field"]').trigger('click')

    expect(wrapper.emitted('override')?.[0]?.[0]).toMatchObject({name: 'Mitbringen', value: 'Schlafsack'})
  })
})
