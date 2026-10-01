/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {formatMemberIds, memberIdsOf, questionKindOf, QuestionKinds} from './questions'

describe('questionKindOf', () => {
  it('reads every spelling of a line of text as text', () => {
    for (const type of ['TEXT', 'STRING', 'LOCATION', 'something else']) {
      expect(questionKindOf(type)).toBe(QuestionKinds.TEXT)
    }
    expect(questionKindOf('TEXTAREA')).toBe(QuestionKinds.LONG_TEXT)
  })

  it('reads a number as a decimal unless the feature asks for a whole one', () => {
    expect(questionKindOf('NUMBER')).toBe(QuestionKinds.DECIMAL)
    expect(questionKindOf('NUMBER', true)).toBe(QuestionKinds.NUMBER)
  })

  it('asks nothing of an age, which counts itself from a date', () => {
    expect(questionKindOf('AGE')).toBeNull()
    expect(questionKindOf('AGE', true)).toBeNull()
  })

  it('reads every date as a date and every choice as a choice', () => {
    for (const type of ['DATE', 'BIRTH_DATE', 'EXPIRY_DATE']) {
      expect(questionKindOf(type)).toBe(QuestionKinds.DATE)
    }
    expect(questionKindOf('ENUM')).toBe(QuestionKinds.CHOICE)
    expect(questionKindOf('BOOLEAN')).toBe(QuestionKinds.BOOLEAN)
    expect(questionKindOf('TIME')).toBe(QuestionKinds.TIME)
    expect(questionKindOf('URL')).toBe(QuestionKinds.URL)
  })

  it('reads the member types as one member or several', () => {
    for (const type of ['MEMBER', 'MEMBER_OF_GROUP', 'MEMBER_OF_TYPE', 'MEMBER_OF_TAG']) {
      expect(questionKindOf(type)).toBe(QuestionKinds.MEMBER)
    }
    for (const type of ['MEMBER_LIST', 'MEMBER_LIST_OF_GROUP', 'MEMBER_LIST_OF_TYPE', 'MEMBER_LIST_OF_TAG']) {
      expect(questionKindOf(type)).toBe(QuestionKinds.MEMBER_LIST)
    }
  })

  it('asks nothing of a heading or of no type at all', () => {
    expect(questionKindOf('SECTION')).toBeNull()
    expect(questionKindOf(null)).toBeNull()
    expect(questionKindOf('')).toBeNull()
  })
})

describe('memberIdsOf', () => {
  it('reads both stored shapes', () => {
    expect(memberIdsOf('12')).toEqual(['12'])
    expect(memberIdsOf('"12"')).toEqual(['12'])
    expect(memberIdsOf('[12,13]')).toEqual(['12', '13'])
    expect(memberIdsOf('["12","13"]')).toEqual(['12', '13'])
  })

  it('reads nothing out of nothing or out of a broken list', () => {
    expect(memberIdsOf('')).toEqual([])
    expect(memberIdsOf('[12,')).toEqual([])
  })

  it('writes one member bare and several as a list', () => {
    expect(formatMemberIds(['12'])).toBe('12')
    expect(formatMemberIds(['12', '13'])).toBe('[12,13]')
    expect(formatMemberIds([])).toBe('')
  })
})
