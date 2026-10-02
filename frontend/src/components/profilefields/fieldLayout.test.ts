/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {FieldType} from '@/api/generated/schema'
import {valueFields} from './fieldLayout'

describe('valueFields', () => {
  it('keeps the questions somebody answers and drops headings, gaps and ages', () => {
    const fields = [
      {fieldType: FieldType.TEXT},
      {fieldType: FieldType.SECTION},
      {fieldType: FieldType.SPACER},
      {fieldType: FieldType.AGE},
      {fieldType: FieldType.BIRTH_DATE},
    ]

    expect(valueFields(fields).map(field => field.fieldType)).toEqual([FieldType.TEXT, FieldType.BIRTH_DATE])
  })
})
