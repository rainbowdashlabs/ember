/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {FieldTypes} from '@/api/fieldTypes'
import {valueFields} from './fieldLayout'

describe('valueFields', () => {
  it('keeps the questions somebody answers and drops headings, gaps and ages', () => {
    const fields = [
      {fieldType: FieldTypes.TEXT},
      {fieldType: FieldTypes.SECTION},
      {fieldType: FieldTypes.SPACER},
      {fieldType: FieldTypes.AGE},
      {fieldType: FieldTypes.BIRTH_DATE},
    ]

    expect(valueFields(fields).map(field => field.fieldType)).toEqual([FieldTypes.TEXT, FieldTypes.BIRTH_DATE])
  })
})
