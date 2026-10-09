/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import {FormStatus, type FormListEntry, type FormRespondent} from '@/api/generated/schema'
import AvailableFormRow from './AvailableFormRow.vue'

function respondent(memberId: number, name: string, hasResponded: boolean, self = false): FormRespondent {
  return {memberId, name, hasResponded, self}
}

function entry(respondents: FormRespondent[], allowEdit = true): FormListEntry {
  return {
    id: 4, stationId: 'station-uid', title: 'Fotoeinwilligung', description: '', status: FormStatus.OPEN,
    startAt: null, endAt: null, responseCount: 1, allowEdit, respondents, restricted: false,
  }
}

function linesOf(row: ReturnType<typeof mount>) {
  return row.findAll('[data-testid="form-respondent"]')
}

/**
 * A guardian answers a form for themselves and for each member in their care, and has to see for whom
 * an answer is already on file. The list used to show one button for the reader alone, so a ward's
 * answer never showed and a missing one could not be told apart.
 */
describe('AvailableFormRow', () => {
  const household = [
    respondent(11, 'Ines', false, true),
    respondent(20, 'Lena', true),
    respondent(21, 'Tom', false),
  ]

  it('shows one line per person with whether they answered', () => {
    const lines = linesOf(mount(AvailableFormRow, {props: {form: entry(household)}}))

    expect(lines).toHaveLength(3)
    expect(lines[0]!.text()).toContain('Ich (Ines)')
    expect(lines[0]!.text()).toContain('Noch offen')
    expect(lines[0]!.text()).toContain('Ausfüllen')
    expect(lines[1]!.text()).toContain('Lena')
    expect(lines[1]!.text()).toContain('Beantwortet')
    expect(lines[1]!.text()).toContain('Antwort bearbeiten')
    expect(lines[2]!.text()).toContain('Tom')
    expect(lines[2]!.text()).toContain('Noch offen')
  })

  it('opens the form for the person whose button was pressed', async () => {
    const row = mount(AvailableFormRow, {props: {form: entry(household)}})

    await linesOf(row)[2]!.find('button').trigger('click')

    const [form, chosen] = row.emitted('fill')![0] as [FormListEntry, FormRespondent]
    expect(form.id).toBe(4)
    expect(chosen.memberId).toBe(21)
  })

  it('offers no change where answers cannot be changed', () => {
    const lines = linesOf(mount(AvailableFormRow, {props: {form: entry(household, false)}}))

    expect(lines[1]!.text()).toContain('Beantwortet')
    expect(lines[1]!.find('button').exists()).toBe(false)
    expect(lines[2]!.find('button').text()).toContain('Ausfüllen')
  })
})
