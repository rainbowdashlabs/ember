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
import type {MemberWithName, StationUserType} from '@/api/generated/schema'
import {createMember} from '@/test/mocks/factories'

function fields(setId: number | null, userTypes: StationUserType[], misfits: MemberWithName[] = []) {
  return mount(GroupRulesFields, {
    props: {
      sets: [{id: 10, stationId: 's', name: 'Stufen'}],
      setId,
      userTypes,
      misfits,
    },
    global: {
      plugins: [createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}})],
      stubs: {NuxtLink: {template: '<a><slot/></a>'}},
    },
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
  it('names the members who would no longer fit, while the types are chosen', () => {
    const view = fields(null, ['TEAM'], [createMember({name: 'Lena Lang'})])

    expect(view.find('[data-testid="group-misfits"]').text()).toContain('Lena Lang')
  })

  it('names nobody while everybody fits', () => {
    expect(fields(null, ['TEAM']).find('[data-testid="group-misfits"]').exists()).toBe(false)
  })
})
