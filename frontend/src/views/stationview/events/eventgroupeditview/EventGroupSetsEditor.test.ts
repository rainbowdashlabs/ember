/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import EventGroupSetsEditor from './EventGroupSetsEditor.vue'
import type {SetRow} from './eventGroupSetRow'

function mountEditor(sets: SetRow[], partCounts: Record<number, number>, storedMinimums: Record<number, number> = {}) {
  return mount(EventGroupSetsEditor, {props: {modelValue: sets, partCounts, storedMinimums}})
}

function minimumInput(editor: ReturnType<typeof mountEditor>) {
  return editor.get('input[data-testid="event-group-set-minimum-0"]')
}

/** A set asks for none to all of its parts, and one without parts yet takes any minimum. */
describe('EventGroupSetsEditor', () => {
  it('keeps the minimum of a set with parts between none and all of them', async () => {
    const editor = mountEditor([{id: 1, name: 'Übung', minimum: 2, maximum: null}], {1: 3})
    const input = minimumInput(editor)

    await input.setValue('7')
    expect(editor.emitted('update:modelValue')!.at(-1)![0]).toEqual([{id: 1, name: 'Übung', minimum: 3, maximum: null}])

    await input.setValue('-2')
    expect(editor.emitted('update:modelValue')!.at(-1)![0]).toEqual([{id: 1, name: 'Übung', minimum: 0, maximum: null}])
    expect(editor.get('[data-testid="event-group-set-hint-0"]').text()).toBe('Termine im Block: 3')
  })

  it('lets a set without parts ask for any minimum, which counts once parts come', async () => {
    const editor = mountEditor([{id: null, name: 'Prüfung', minimum: 1, maximum: null}], {})
    const input = minimumInput(editor)

    await input.setValue('4')
    expect(editor.emitted('update:modelValue')!.at(-1)![0]).toEqual([{id: null, name: 'Prüfung', minimum: 4, maximum: null}])
    expect(editor.get('[data-testid="event-group-set-hint-0"]').text()).toBe('gilt, sobald der Block Termine hat')
    expect(editor.find('[data-testid="event-group-set-hint-0"] [data-testid="hint-icon"]').exists(),
        'the hint is an info icon in the row, beside the numbers it is about').toBe(true)
  })

  it('shows the number the set holds once a typed one past the bound is left', async () => {
    const editor = mountEditor([{id: 1, name: 'Übung', minimum: 3, maximum: null}], {1: 3})
    const input = minimumInput(editor)

    await input.setValue('7')
    await input.trigger('focusout')

    expect(editor.emitted('update:modelValue')!.at(-1)![0]).toEqual([{id: 1, name: 'Übung', minimum: 3, maximum: null}])
    expect((input.element as HTMLInputElement).value).toBe('3')
  })

  it('lets a set still filling up keep the minimum it was saved with, but not a new one above its parts', async () => {
    const editor = mountEditor([{id: 1, name: 'Übung', minimum: 3, maximum: null}], {1: 1}, {1: 3})
    const input = minimumInput(editor)
    expect(editor.get('[data-testid="event-group-set-hint-0"]').text()).toBe('Termine im Block: 1, die Zahl gilt, sobald es 3 sind')

    await input.setValue('2')
    expect(editor.emitted('update:modelValue')!.at(-1)![0]).toEqual([{id: 1, name: 'Übung', minimum: 1, maximum: null}])

    await input.setValue('3')
    expect(editor.emitted('update:modelValue')!.at(-1)![0]).toEqual([{id: 1, name: 'Übung', minimum: 3, maximum: null}])
  })

  /** A maximum is empty for none, at least 1 and at least the minimum, and a minimum past it takes it along. */
  it('keeps the maximum at least the minimum and raises it with the minimum', async () => {
    const editor = mountEditor([{id: 1, name: 'Kurs', minimum: 1, maximum: null}], {1: 3})
    const maximum = editor.get('input[data-testid="event-group-set-maximum-0"]')

    await maximum.setValue('1')
    expect(editor.emitted('update:modelValue')!.at(-1)![0]).toEqual([{id: 1, name: 'Kurs', minimum: 1, maximum: 1}])

    await editor.setProps({modelValue: [{id: 1, name: 'Kurs', minimum: 2, maximum: null}]})
    await maximum.setValue('1')
    expect(editor.emitted('update:modelValue')!.at(-1)![0]).toEqual([{id: 1, name: 'Kurs', minimum: 2, maximum: 2}])

    await maximum.setValue('')
    expect(editor.emitted('update:modelValue')!.at(-1)![0]).toEqual([{id: 1, name: 'Kurs', minimum: 2, maximum: null}])

    await editor.setProps({modelValue: [{id: 1, name: 'Kurs', minimum: 1, maximum: 1}]})
    await minimumInput(editor).setValue('2')
    expect(editor.emitted('update:modelValue')!.at(-1)![0]).toEqual([{id: 1, name: 'Kurs', minimum: 2, maximum: 2}])
  })

  /** Two bare number fields say nothing on their own, so the columns are named above them. */
  it('names the columns of the minimum and the maximum', () => {
    const editor = mountEditor([{id: 1, name: 'Übung', minimum: 1, maximum: 1}], {1: 2})

    const header = editor.get('[data-testid="event-group-sets-header"]').text()
    expect(header).toContain('Termine mindestens')
    expect(header).toContain('Termine höchstens')
  })

  /** Below the width the header shows at, each number field names itself. */
  it('names both number fields in the row for narrow screens', () => {
    const editor = mountEditor([{id: 1, name: 'Übung', minimum: 1, maximum: 1}], {1: 2})

    const narrow = editor.findAll('.sm\\:hidden').map(label => label.text())
    expect(narrow).toEqual(['Termine mindestens', 'Termine höchstens'])
  })
})
