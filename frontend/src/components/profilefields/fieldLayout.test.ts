/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {FieldType} from '@/api/generated/schema'
import {FieldWidths, snapWidth, spanClass, spanForWidth, valueFields, widthOf} from './fieldLayout'

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

/** Every width is a whole span of a twelve-column row, and a narrow screen draws every field full. */
describe('field widths', () => {
  it('lays every width out on twelve columns, full below the small breakpoint', () => {
    expect(spanForWidth(FieldWidths.QUARTER)).toBe('col-span-12 sm:col-span-3')
    expect(spanForWidth(FieldWidths.THIRD)).toBe('col-span-12 sm:col-span-4')
    expect(spanForWidth(FieldWidths.HALF)).toBe('col-span-12 sm:col-span-6')
    expect(spanForWidth(FieldWidths.TWO_THIRDS)).toBe('col-span-12 sm:col-span-8')
    expect(spanForWidth(FieldWidths.THREE_QUARTERS)).toBe('col-span-12 sm:col-span-9')
    expect(spanForWidth(FieldWidths.FULL)).toBe('col-span-12')
  })

  it('draws a width it does not know, or none, as a whole row', () => {
    expect(spanForWidth('enormous')).toBe('col-span-12')
    expect(spanForWidth(undefined)).toBe('col-span-12')
    expect(widthOf({width: 'enormous'})).toBe(FieldWidths.FULL)
    expect(widthOf({width: FieldWidths.TWO_THIRDS})).toBe(FieldWidths.TWO_THIRDS)
  })

  it('draws a heading across the whole row whatever width it carries', () => {
    expect(spanClass({fieldType: FieldType.SECTION, width: FieldWidths.QUARTER})).toBe('col-span-12')
  })

  it('snaps a dragged span to the nearest width, the narrower one on a tie', () => {
    expect(snapWidth(0)).toBe(FieldWidths.QUARTER)
    expect(snapWidth(4.4)).toBe(FieldWidths.THIRD)
    expect(snapWidth(5)).toBe(FieldWidths.THIRD)
    expect(snapWidth(7.2)).toBe(FieldWidths.TWO_THIRDS)
    expect(snapWidth(8.6)).toBe(FieldWidths.THREE_QUARTERS)
    expect(snapWidth(11)).toBe(FieldWidths.FULL)
    expect(snapWidth(20)).toBe(FieldWidths.FULL)
  })
})
