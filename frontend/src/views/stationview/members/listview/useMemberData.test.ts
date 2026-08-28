/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import {describe, expect, it} from 'vitest'
import type {RichMember} from '@/api/stationMembers'
import {useMemberData, type MemberDataSource} from './useMemberData'

function richMember(id: number, name: string, mailReachable: boolean): RichMember {
  return {
    id,
    stationId: 1,
    accountId: id,
    name,
    email: `member-${id}@example.test`,
    accountSetupPending: true,
    setupMailExpiresAt: '2026-09-01T10:00:00Z',
    mailReachable,
    former: false,
    userType: 'MEMBER',
    roles: [],
    groups: [],
    tags: [],
    profileValues: {},
    identity: null,
  }
}

function sourceOf(members: RichMember[]): MemberDataSource {
  return {load: async () => ({members, fields: [], roles: []})}
}

/** The composable reaches for the locale, so it is used from inside a component as the app does. */
function dataFor(members: RichMember[]) {
  let api: ReturnType<typeof useMemberData> | null = null
  mount(defineComponent({
    setup() {
      api = useMemberData(sourceOf(members))
      return () => null
    },
  }))
  return api as unknown as ReturnType<typeof useMemberData>
}

describe('useMemberData', () => {
  /**
   * The row offers to send the setup mail again only where it could arrive, and it reads that off
   * the member it was handed. The flag went missing in this mapping once, and the button was then
   * offered for every address nothing can be delivered to.
   */
  it('carries whether a member can be written to at all', async () => {
    const data = dataFor([
      richMember(1, 'Kann Empfangen', true),
      richMember(2, 'Ohne Postfach', false),
    ])

    await data.reload()

    expect(data.members.value.map(member => member.mailReachable)).toEqual([true, false])
  })

  it('carries when the setup mail runs out', async () => {
    const data = dataFor([richMember(1, 'Wartet Noch', true)])

    await data.reload()

    expect(data.members.value[0]?.setupMailExpiresAt).toBe('2026-09-01T10:00:00Z')
  })
})
