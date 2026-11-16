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
    const editor = mountEditor([{id: 1, name: 'Übung', minimum: 2}], {1: 3})
    const input = minimumInput(editor)

    await input.setValue('7')
    expect(editor.emitted('update:modelValue')!.at(-1)![0]).toEqual([{id: 1, name: 'Übung', minimum: 3}])

    await input.setValue('-2')
    expect(editor.emitted('update:modelValue')!.at(-1)![0]).toEqual([{id: 1, name: 'Übung', minimum: 0}])
    expect(editor.get('[data-testid="event-group-set-hint-0"]').text()).toBe('Termine im Block: 3')
  })

  it('lets a set without parts ask for any minimum, which counts once parts come', async () => {
    const editor = mountEditor([{id: null, name: 'Prüfung', minimum: 1}], {})
    const input = minimumInput(editor)

    await input.setValue('4')
    expect(editor.emitted('update:modelValue')!.at(-1)![0]).toEqual([{id: null, name: 'Prüfung', minimum: 4}])
    expect(editor.get('[data-testid="event-group-set-hint-0"]').text()).toBe('gilt, sobald der Block Termine hat')
  })

  it('shows the number the set holds once a typed one past the bound is left', async () => {
    const editor = mountEditor([{id: 1, name: 'Übung', minimum: 3}], {1: 3})
    const input = minimumInput(editor)

    await input.setValue('7')
    await input.trigger('focusout')

    expect(editor.emitted('update:modelValue')!.at(-1)![0]).toEqual([{id: 1, name: 'Übung', minimum: 3}])
    expect((input.element as HTMLInputElement).value).toBe('3')
  })

  it('lets a set still filling up keep the minimum it was saved with, but not a new one above its parts', async () => {
    const editor = mountEditor([{id: 1, name: 'Übung', minimum: 3}], {1: 1}, {1: 3})
    const input = minimumInput(editor)
    expect(editor.get('[data-testid="event-group-set-hint-0"]').text()).toBe('Termine im Block: 1, die Zahl gilt, sobald es 3 sind')

    await input.setValue('2')
    expect(editor.emitted('update:modelValue')!.at(-1)![0]).toEqual([{id: 1, name: 'Übung', minimum: 1}])

    await input.setValue('3')
    expect(editor.emitted('update:modelValue')!.at(-1)![0]).toEqual([{id: 1, name: 'Übung', minimum: 3}])
  })
})
