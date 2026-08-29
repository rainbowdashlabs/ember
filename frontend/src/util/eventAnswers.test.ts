/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {EventFieldTypes, RegistrationStatus, type EventRegistrationEntry, type EventRegistrationField} from '@/api/events'
import {answerTotals} from './eventAnswers'

const MEALS: EventRegistrationField = {
  id: 1,
  name: 'Ernährung',
  fieldType: EventFieldTypes.ENUM,
  config: {options: ['Mischkost', 'Vegetarisch'], required: true},
  overview: true,
}

const GUESTS: EventRegistrationField = {
  id: 2,
  name: 'Begleitung',
  fieldType: EventFieldTypes.NUMBER,
  config: {},
  overview: true,
}

function registration(id: number, status: string, answers: Record<number, string>): EventRegistrationEntry {
  return {
    id,
    eventId: 13,
    memberId: id,
    memberName: `Mitglied ${id}`,
    eventDate: '2026-09-01',
    status,
    createdAt: '2026-08-01T10:00:00Z',
    fields: Object.entries(answers).map(([fieldId, value]) => ({fieldId: Number(fieldId), value})),
  }
}

describe('answerTotals', () => {
  /**
   * An answer outlives the place it was given with, so somebody turned away or who called off still
   * has their catering choice on file. Counting those had a station ordering food for people who
   * were told not to come.
   */
  it('counts only the answers of people who have a place', () => {
    const totals = answerTotals([MEALS, GUESTS], [
      registration(1, RegistrationStatus.ACCEPTED, {1: 'Mischkost', 2: '2'}),
      registration(2, RegistrationStatus.ACCEPTED, {1: 'Vegetarisch', 2: '1'}),
      registration(3, RegistrationStatus.DENIED, {1: 'Mischkost', 2: '5'}),
      registration(4, RegistrationStatus.DECLINED, {1: 'Vegetarisch', 2: '5'}),
      registration(5, RegistrationStatus.WITHDRAWN, {1: 'Mischkost', 2: '5'}),
    ])

    expect(totals).toEqual([
      {label: 'Ernährung', text: 'Mischkost 1, Vegetarisch 1'},
      {label: 'Begleitung', text: '3'},
    ])
  })

  /** Somebody still waiting on an answer has asked for a place rather than been given one. */
  it('leaves out an answer still waiting on a decision', () => {
    const totals = answerTotals([GUESTS], [
      registration(1, RegistrationStatus.ACCEPTED, {2: '2'}),
      registration(2, RegistrationStatus.PENDING, {2: '4'}),
    ])

    expect(totals).toEqual([{label: 'Begleitung', text: '2'}])
  })

  it('says nothing about a choice nobody made', () => {
    const totals = answerTotals([MEALS], [registration(1, RegistrationStatus.DENIED, {1: 'Mischkost'})])

    expect(totals).toEqual([])
  })

  it('has no total to show for free text', () => {
    const text: EventRegistrationField = {...MEALS, id: 3, name: 'Hinweis', fieldType: EventFieldTypes.STRING, config: {}}

    const totals = answerTotals([text], [registration(1, RegistrationStatus.ACCEPTED, {3: 'Bitte früher'})])

    expect(totals).toEqual([])
  })
})
