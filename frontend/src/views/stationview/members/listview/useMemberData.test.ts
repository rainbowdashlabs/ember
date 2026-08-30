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
import {getMemberFirstName, getMemberLastName, useMemberData, type MemberDataSource} from './useMemberData'

function richMember(id: number, name: string, mailReaches: RichMember['mailReaches']): RichMember {
  return {
    id,
    stationId: 1,
    accountId: id,
    name,
    firstName: name.split(' ')[0] ?? '',
    lastName: name.split(' ').slice(1).join(' '),
    email: `member-${id}@example.test`,
    accountSetupPending: true,
    setupMailExpiresAt: '2026-09-01T10:00:00Z',
    mailReaches,
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
  it('carries who a letter about a member arrives at', async () => {
    const data = dataFor([
      richMember(1, 'Eigene Adresse', 'SELF'),
      richMember(2, 'Über die Eltern', 'GUARDIANS'),
      richMember(3, 'Ohne Postfach', 'NOBODY'),
    ])

    await data.reload()

    expect(data.members.value.map(member => member.mailReaches)).toEqual(['SELF', 'GUARDIANS', 'NOBODY'])
  })

  /**
   * The reported bug: the edit screen split the whole name at the first space, so somebody stored
   * as "Millie Jo" and "Harnack" was offered a surname of "Jo Harnack". Correcting that saved the
   * right thing and the next load split the whole name again, which read as a change not kept.
   */
  it('carries the two halves of a name as they are stored', async () => {
    const stored = {...richMember(1, 'Millie Jo Harnack', 'SELF'), firstName: 'Millie Jo', lastName: 'Harnack'}
    const data = dataFor([stored])

    await data.reload()

    const member = data.members.value[0]!
    expect(getMemberFirstName(member)).toBe('Millie Jo')
    expect(getMemberLastName(member), 'rather than the guess "Jo Harnack"').toBe('Harnack')
  })

  it('carries when the setup mail runs out', async () => {
    const data = dataFor([richMember(1, 'Wartet Noch', 'SELF')])

    await data.reload()

    expect(data.members.value[0]?.setupMailExpiresAt).toBe('2026-09-01T10:00:00Z')
  })
})
