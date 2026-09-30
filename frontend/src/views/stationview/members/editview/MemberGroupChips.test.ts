/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import MemberGroupChips from './MemberGroupChips.vue'
import deDE from '@/i18n/de-DE'
import type {MemberGroup} from '@/api/types'

const beginners: MemberGroup = {id: 1, name: 'Anfänger', groupSetId: 10}
const advanced: MemberGroup = {id: 2, name: 'Fortgeschritten', groupSetId: 10}
const trainers: MemberGroup = {id: 3, name: 'Ausbilder', userTypes: ['TEAM', 'MANAGER']}
const swimmers: MemberGroup = {id: 4, name: 'Schwimmer'}

function chips(selected: number[], userType = 'MEMBER') {
  return mount(MemberGroupChips, {
    props: {
      groups: [beginners, advanced, trainers, swimmers],
      sets: [{id: 10, stationId: 's', name: 'Stufen'}],
      selected: new Set(selected),
      userType,
    },
    global: {plugins: [createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}})]},
  })
}

function chip(view: ReturnType<typeof chips>, label: string) {
  const found = view.findAll('button').find(button => button.text() === label)
  if (!found) throw new Error(`no chip ${label}`)
  return found
}

/** A member's groups on their edit page: one choice per set, and groups of other types closed. */
describe('MemberGroupChips', () => {
  it('offers a set as one choice, so picking another group of it is a move', async () => {
    const view = chips([1, 4])

    expect(view.text()).toContain('Stufen')
    await chip(view, 'Fortgeschritten').trigger('click')

    expect([...(view.emitted('change')?.[0]?.[0] as Set<number>)].sort()).toEqual([2, 4])
  })

  it('takes a member out of every group of a set with none', async () => {
    const view = chips([1, 4])

    await chip(view, deDE.memberEdit.groupSetNone).trigger('click')

    expect([...(view.emitted('change')?.[0]?.[0] as Set<number>)]).toEqual([4])
  })

  it('closes a group bound to other types and says which it takes', () => {
    const view = chips([], 'MEMBER')

    expect(chip(view, 'Ausbilder').attributes('disabled')).toBeDefined()
    expect(view.text()).toContain('Nur für Team, Manager')
    expect(chip(view, 'Schwimmer').attributes('disabled')).toBeUndefined()
    expect(chips([], 'TEAM').text()).not.toContain('Nur für')
  })
})
