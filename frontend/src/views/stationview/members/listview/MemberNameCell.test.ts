/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import MemberNameCell from './MemberNameCell.vue'
import deDE from '@/i18n/de-DE'
import {createIdentity} from '@/test/mocks/factories'
import type {RosterMember} from './useMemberData'

function member(overrides: Partial<RosterMember> = {}): RosterMember {
  return {
    id: 1,
    stationId: '1',
    accountId: 1,
    name: 'Clara Voll',
    firstName: 'Clara',
    lastName: 'Voll',
    email: 'clara@test.com',
    accountSetupPending: false,
    setupMailExpiresAt: null,
    mailReaches: 'SELF',
    former: false,
    roles: [],
    groups: [],
    tags: [],
    profileValues: {},
    userType: 'MEMBER',
    identity: createIdentity({name: 'Clara Voll'}),
    ...overrides,
  }
}

function cell(overrides: Partial<RosterMember> = {}) {
  return mount(MemberNameCell, {
    props: {member: member(overrides), canEdit: true},
    global: {stubs: {MemberName: true, MemberSetupIndicator: true}},
  })
}

/** The badge beside a name in the member list that says a required profile question is still open. */
describe('MemberNameCell', () => {
  it('marks a member whose profile leaves a required question open', () => {
    const badge = cell({profileComplete: false}).find('[data-testid="member-incomplete"]')

    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe(deDE.membersList.incomplete)
  })

  it('leaves a complete profile unmarked', () => {
    expect(cell({profileComplete: true}).find('[data-testid="member-incomplete"]').exists()).toBe(false)
  })

  it('says nothing where the list does not know, as on an association list', () => {
    expect(cell().find('[data-testid="member-incomplete"]').exists()).toBe(false)
  })

  it('marks a member that waits for somebody to link their existing account', () => {
    const badge = cell({accountId: null, linkStatus: 'WAITING'}).find('[data-testid="member-link-waiting"]')

    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe(deDE.memberLinks.badge.WAITING)
  })

  it('marks a declined link, and nothing once the account is linked', () => {
    expect(cell({accountId: null, linkStatus: 'DECLINED'}).find('[data-testid="member-link-declined"]').exists())
        .toBe(true)
    expect(cell({accountId: 4, linkStatus: 'ACCEPTED'}).find('[data-testid^="member-link"]').exists()).toBe(false)
  })
})
