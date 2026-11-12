/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {createGroup, createMember} from '@/test/mocks/factories'
import {useGroupRulesForm} from './useGroupRulesForm'

/**
 * The members who would not fit the ticked types are known while they are ticked, and a save made
 * with them on screen asks for them to be taken out.
 */
describe('useGroupRulesForm', () => {
  it('reads the misfits from the ticked types as they change', () => {
    const rules = useGroupRulesForm()
    const team = createMember({userType: 'TEAM'})
    const member = createMember({userType: 'MEMBER'})
    const opening = rules.load(createGroup())
    rules.holding([team, member], opening)

    expect(rules.misfits.value, 'no type ticked takes everybody').toEqual([])
    expect(rules.removesMisfits()).toBe(false)

    rules.userTypes.value = ['TEAM']

    expect(rules.misfits.value).toEqual([member])
    expect(rules.removesMisfits()).toBe(true)
  })

  it('forgets the members of the group edited before', () => {
    const rules = useGroupRulesForm()
    const opening = rules.load(createGroup())
    rules.holding([createMember({userType: 'MEMBER'})], opening)
    rules.userTypes.value = ['TEAM']

    rules.load(null)

    expect(rules.misfits.value).toEqual([])
  })

  /** A slow answer for a form opened earlier never lands in the form open now. */
  it('drops members fetched for a form that is no longer open', () => {
    const rules = useGroupRulesForm()
    const earlier = rules.load(createGroup())
    const now = rules.load(createGroup({userTypes: ['TEAM']}))

    rules.holding([createMember({userType: 'MEMBER'})], earlier)
    expect(rules.misfits.value, 'the earlier form\'s members are dropped').toEqual([])

    const own = createMember({userType: 'MEMBER'})
    rules.holding([own], now)
    expect(rules.misfits.value).toEqual([own])
  })
})
