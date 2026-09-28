/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {EventFieldTypes, RegistrationStatus, type EventRegistrationEntry, type EventRegistrationField} from '@/api/events'
import {answerTotals, localAnswers, membersToRegister} from './eventAnswers'

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

/** A member as the member menu offers them, which carries the id as text. */
function member(id: number) {
  return {value: String(id), name: `Mitglied ${id}`}
}

/** A registration of the same member on another date of the appointment. */
function onAnotherDate(id: number, status: string): EventRegistrationEntry {
  return {...registration(id, status, {}), eventDate: '2026-09-08'}
}

describe('membersToRegister', () => {
  const members = [1, 2, 3, 4, 5, 6].map(member)

  /**
   * A place given back, a refusal and a request turned away all keep their row. Reading any row as a
   * place left those members out of the list, so not even a manager could put them back on.
   */
  it('offers everybody who neither holds a place nor waits for one', () => {
    const offered = membersToRegister(members, [
      registration(1, RegistrationStatus.ACCEPTED, {}),
      registration(2, RegistrationStatus.PENDING, {}),
      registration(3, RegistrationStatus.WITHDRAWN, {}),
      registration(4, RegistrationStatus.DECLINED, {}),
      registration(5, RegistrationStatus.DENIED, {}),
    ], '2026-09-01')

    expect(offered.map(entry => entry.value)).toEqual(['3', '4', '5', '6'])
  })

  it('reads only the rows of the date the registration is made for', () => {
    const offered = membersToRegister(
        members.slice(0, 2),
        [onAnotherDate(1, RegistrationStatus.ACCEPTED), registration(2, RegistrationStatus.ACCEPTED, {})],
        '2026-09-01')

    expect(offered.map(entry => entry.value)).toEqual(['1'])
  })

  it('reads every row where no date is in view', () => {
    const offered = membersToRegister(members.slice(0, 2), [onAnotherDate(1, RegistrationStatus.ACCEPTED)], null)

    expect(offered.map(entry => entry.value)).toEqual(['2'])
  })
})

describe('localAnswers', () => {
  const people = [{key: 1, name: 'Ich'}, {key: 2, name: 'Kind'}]

  /** Whoever gave a place back holds nothing and owes an answer, so they are offered to sign up again. */
  it('leaves out a place given back and keeps a refusal', () => {
    const answers = localAnswers(people, [
      registration(1, RegistrationStatus.WITHDRAWN, {}),
      registration(2, RegistrationStatus.DECLINED, {}),
    ])

    expect(answers.map(answer => [answer.key, answer.status])).toEqual([[2, RegistrationStatus.DECLINED]])
  })
})
