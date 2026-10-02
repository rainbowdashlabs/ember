/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import GroupRulesFields from './GroupRulesFields.vue'
import deDE from '@/i18n/de-DE'
import type {StationUserType} from '@/api/generated/schema'

function fields(setId: number | null, userTypes: StationUserType[]) {
  return mount(GroupRulesFields, {
    props: {
      sets: [{id: 10, stationId: 's', name: 'Stufen'}],
      setId,
      userTypes,
    },
    global: {plugins: [createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}})]},
  })
}

/** The rules of a group in its form: the set it belongs to and the member types it takes. */
describe('GroupRulesFields', () => {
  it('binds a group to a type and frees it again', async () => {
    const view = fields(null, ['MEMBER'])
    const team = view.findAll('button').find(button => button.text() === 'Team')!
    const member = view.findAll('button').find(button => button.text() === 'Mitglied')!

    await team.trigger('click')
    await member.trigger('click')

    expect(view.emitted('update:userTypes')).toEqual([[['MEMBER', 'TEAM']], [['TEAM']]])
  })

  it('puts the group into a set and out of every set', async () => {
    const view = fields(10, [])
    const select = view.find('select')

    expect(view.text()).toContain('Stufen')
    await select.setValue('')
    await select.setValue('10')

    expect(view.emitted('update:setId')).toEqual([[null], [10]])
  })
})
