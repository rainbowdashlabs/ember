/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {ref} from 'vue'
import {listSearch, numericPickerModel} from './listSearch'

interface Row {
  name: string
  drawer: string
}

const ROWS: Row[] = [
  {name: 'Funkgerät blau', drawer: 'Handfunkgeräte'},
  {name: 'Funkgerät grün', drawer: 'Handfunkgeräte'},
  {name: 'Ladestation', drawer: 'Sonstiges'},
]

const search = listSearch(ref(ROWS), row => `${row.name} ${row.drawer}`)

describe('listSearch', () => {
  it('offers everything while nothing is typed', async () => {
    expect(await search('')).toHaveLength(3)
  })

  it('narrows with every further word rather than widening', async () => {
    expect((await search('funk')).map(row => row.name)).toEqual(['Funkgerät blau', 'Funkgerät grün'])
    expect((await search('funk blau')).map(row => row.name)).toEqual(['Funkgerät blau'])
  })

  it('matches without regard to case', async () => {
    expect(await search('LADESTATION')).toHaveLength(1)
  })

  it('finds a row by what it is filed under', async () => {
    expect(await search('sonstiges')).toHaveLength(1)
  })

  it('caps what it offers, because a list of everything is the dropdown it replaced', async () => {
    const many = Array.from({length: 60}, (_, i) => ({name: `Stück ${i}`, drawer: 'Lager'}))

    expect(await listSearch(ref(many), row => row.name)('stück')).toHaveLength(25)
  })
})

describe('numericPickerModel', () => {
  it('reads a number as the string a picker speaks in', () => {
    expect(numericPickerModel(ref<number | null>(7)).value).toBe('7')
  })

  it('reads nothing chosen as nothing chosen', () => {
    expect(numericPickerModel(ref<number | null>(null)).value).toBeNull()
  })

  it('writes a chosen string back as a number', () => {
    const model = ref<number | null>(null)
    numericPickerModel(model).value = '7'

    expect(model.value).toBe(7)
  })

  it('writes a cleared picker back as nothing chosen', () => {
    const model = ref<number | null>(7)
    const picker = numericPickerModel(model)

    picker.value = null
    expect(model.value).toBeNull()

    model.value = 7
    picker.value = ''
    expect(model.value).toBeNull()
  })
})
