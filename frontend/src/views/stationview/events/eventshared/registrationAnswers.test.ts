/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {EventRegistrationField} from '@/api/generated/schema'
import {answersToSend, missingAnswers, numberRange, seedAnswers} from './registrationAnswers'

function question(id: number, fieldType: string, config: Record<string, unknown> = {}): EventRegistrationField {
  return {id, eventId: 3, eventGroupId: null, name: `Frage ${id}`, fieldType, config, position: id, overview: false} as EventRegistrationField
}

describe('registrationAnswers', () => {
  it('starts from the answer on file, then the default, then nothing', () => {
    const fields = [question(1, 'TEXT', {defaultValue: 'M'}), question(2, 'TEXT', {defaultValue: 'keine'}), question(3, 'TEXT')]

    expect(seedAnswers(fields)).toEqual({1: 'M', 2: 'keine', 3: ''})
    expect(seedAnswers(fields, [{fieldId: 1, value: 'L'}])).toEqual({1: 'L', 2: 'keine', 3: ''})
  })

  it('names the required questions without an answer', () => {
    const fields = [question(1, 'TEXT', {required: true}), question(2, 'TEXT', {required: true}), question(3, 'TEXT')]

    expect(missingAnswers(fields, {1: ' ', 2: 'ja', 3: ''}).map(field => field.id)).toEqual([1])
  })

  it('sends the given answers only', () => {
    const fields = [question(1, 'TEXT'), question(2, 'TEXT')]

    expect(answersToSend(fields, {1: 'M', 2: '  '})).toEqual([{fieldId: 1, value: 'M'}])
  })

  it('gives the bounds of a number question with an open end where one is missing', () => {
    expect(numberRange(question(1, 'NUMBER', {min: 1, max: 5}))).toEqual({min: 1, max: 5})
    expect(numberRange(question(1, 'NUMBER', {min: 2}))).toEqual({min: 2, max: '–'})
    expect(numberRange(question(1, 'NUMBER'))).toBeNull()
    expect(numberRange(question(1, 'TEXT', {min: 1}))).toBeNull()
  })
})
