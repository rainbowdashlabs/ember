/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {answerText, formatMemberIds, isYes, memberIdsOf, questionKindOf, QuestionKinds} from './questions'

describe('questionKindOf', () => {
  it('reads a line of text and a place as text, several lines as long text', () => {
    for (const type of ['TEXT', 'LOCATION']) {
      expect(questionKindOf(type)).toBe(QuestionKinds.TEXT)
    }
    expect(questionKindOf('LONG_TEXT')).toBe(QuestionKinds.LONG_TEXT)
  })

  it('reads a number as a whole one unless its step is below one', () => {
    expect(questionKindOf('NUMBER')).toBe(QuestionKinds.NUMBER)
    expect(questionKindOf('NUMBER', 1)).toBe(QuestionKinds.NUMBER)
    expect(questionKindOf('NUMBER', 0.5)).toBe(QuestionKinds.DECIMAL)
  })

  it('asks nothing of an age, which counts itself from a date', () => {
    expect(questionKindOf('AGE')).toBeNull()
  })

  it('reads every date as a date and every choice as a choice', () => {
    for (const type of ['DATE', 'BIRTH_DATE', 'EXPIRY_DATE']) {
      expect(questionKindOf(type)).toBe(QuestionKinds.DATE)
    }
    expect(questionKindOf('CHOICE')).toBe(QuestionKinds.CHOICE)
    expect(questionKindOf('BOOLEAN')).toBe(QuestionKinds.BOOLEAN)
    expect(questionKindOf('TIME')).toBe(QuestionKinds.TIME)
    expect(questionKindOf('URL')).toBe(QuestionKinds.URL)
  })

  it('reads the member types as one member or several', () => {
    for (const type of ['MEMBER', 'MEMBER_OF_GROUP', 'MEMBER_OF_TYPE', 'MEMBER_OF_TAG', 'LANE_ASSIGNEE']) {
      expect(questionKindOf(type)).toBe(QuestionKinds.MEMBER)
    }
    for (const type of ['MEMBER_LIST', 'MEMBER_LIST_OF_GROUP', 'MEMBER_LIST_OF_TYPE', 'MEMBER_LIST_OF_TAG']) {
      expect(questionKindOf(type)).toBe(QuestionKinds.MEMBER_LIST)
    }
  })

  it('asks nothing of a heading, a gap or of no type at all', () => {
    expect(questionKindOf('SECTION')).toBeNull()
    expect(questionKindOf('SPACER')).toBeNull()
    expect(questionKindOf(null)).toBeNull()
    expect(questionKindOf('')).toBeNull()
  })
})

describe('isYes', () => {
  it('reads every way a yes has been stored', () => {
    for (const said of [true, 1, 'true', '1', '"true"', ' TRUE ']) {
      expect(isYes(said)).toBe(true)
    }
  })

  it('reads everything else as no', () => {
    for (const said of [false, 0, 'false', '0', '', null, undefined, 'yes please']) {
      expect(isYes(said)).toBe(false)
    }
  })
})

describe('memberIdsOf', () => {
  it('reads both stored shapes', () => {
    expect(memberIdsOf('12')).toEqual(['12'])
    expect(memberIdsOf('"12"')).toEqual(['12'])
    expect(memberIdsOf('[12,13]')).toEqual(['12', '13'])
    expect(memberIdsOf('["12","13"]')).toEqual(['12', '13'])
  })

  it('reads an answer that arrives already parsed', () => {
    expect(memberIdsOf(12)).toEqual(['12'])
    expect(memberIdsOf([12, 13])).toEqual(['12', '13'])
  })

  it('reads nothing out of nothing or out of a broken list', () => {
    expect(memberIdsOf('')).toEqual([])
    expect(memberIdsOf(null)).toEqual([])
    expect(memberIdsOf('[12,')).toEqual([])
  })

  it('writes one member bare and several as a list', () => {
    expect(formatMemberIds(['12'])).toBe('12')
    expect(formatMemberIds(['12', '13'])).toBe('[12,13]')
    expect(formatMemberIds([])).toBe('')
  })
})

describe('answerText', () => {
  const words = {yes: 'Ja', no: 'Nein', names: new Map([[12, 'Anna'], [13, 'Ben']])}

  it('writes a yes however it was stored', () => {
    expect(answerText('BOOLEAN', 'true', words)).toBe('Ja')
    expect(answerText('BOOLEAN', '1', words)).toBe('Ja')
    expect(answerText('BOOLEAN', true, words)).toBe('Ja')
    expect(answerText('BOOLEAN', 'false', words)).toBe('Nein')
  })

  it('names the members an answer names, and the number of one nobody knows', () => {
    expect(answerText('MEMBER_LIST_OF_GROUP', '[12,13]', words)).toBe('Anna, Ben')
    expect(answerText('MEMBER', '14', words)).toBe('#14')
  })

  it('writes a day and a time the way a reader writes them', () => {
    expect(answerText('DATE', '2026-10-12', words)).toBe('12.10.2026')
    expect(answerText('TIME', '09:30', words)).toBe('09:30')
  })

  it('writes nothing for no answer and everything else as it stands', () => {
    expect(answerText('TEXT', '', words)).toBe('')
    expect(answerText('TEXT', null, words)).toBe('')
    expect(answerText('NUMBER', 3, words)).toBe('3')
  })
})
