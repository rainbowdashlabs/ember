/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import {ref} from 'vue'
import SetupMailChoice from './SetupMailChoice.vue'
import type {SessionInfo} from '@/api/generated/schema'

const sessionInfo = ref<SessionInfo | null>(null)

vi.mock('@/composables/useSession', () => ({useSession: () => ({sessionInfo})}))

const CHOICE = '[data-testid="setup-mail-choice"]'
const IMPOSSIBLE = '[data-testid="setup-mail-impossible"]'

function choice(props: {modelValue?: boolean; hasAddress?: boolean} = {}) {
  return mount(SetupMailChoice, {props})
}

/** A manager signed in on an instance that can or cannot send mail. */
function signedIn(canSendMail: boolean): SessionInfo {
  return {
    account: {id: 1, uid: null, email: 'manager@example.org', username: null, firstName: 'Mia', lastName: 'Muster'},
    canSendMail,
    clusterId: null,
    clusterPermissions: [],
    clusterUserType: null,
    disabledModules: [],
    groupIds: [],
    groups: [],
    instanceUserType: 'USER',
    managedMembers: [],
    member: null,
    ownStationPermissions: [],
    pdfHidesInstanceUrl: false,
    permissions: [],
    profileComplete: true,
    publicKbMode: null,
    roleIds: [],
    setupCompletedAt: null,
    stationId: null,
    stationTimezone: 'UTC',
    tagIds: [],
    tags: [],
    theme: {
      instanceDefaultTheme: 'ember',
      instanceDefaultFeel: 'ROUNDED',
      instanceLockFeel: false,
      defaultTheme: 'ember',
      defaultFeel: 'ROUNDED',
      allowUserTheme: true,
      allowUserFeel: true,
      customThemeColors: null,
      userTheme: null,
      userDarkMode: null,
      userFeel: null,
    },
    userType: 'MANAGER',
  }
}

afterEach(() => {
  sessionInfo.value = null
})

describe('SetupMailChoice', () => {
  it('offers the choice where the instance can send', () => {
    sessionInfo.value = signedIn(true)

    expect(choice().find(CHOICE).exists()).toBe(true)
  })

  it('offers the choice before the session has said anything about sending', () => {
    sessionInfo.value = null

    expect(choice().find(CHOICE).exists()).toBe(true)
  })

  it('sends at once until somebody says otherwise', () => {
    sessionInfo.value = signedIn(true)

    expect(choice().get(CHOICE).get('[role="switch"]').attributes('aria-checked')).toBe('true')
  })

  it('says what holding the mail back means', async () => {
    sessionInfo.value = signedIn(true)
    const wrapper = choice({modelValue: false})

    expect(wrapper.get(CHOICE).text()).toContain('Mitgliederliste')
  })

  it('asks nothing where the instance has nowhere to send through', () => {
    sessionInfo.value = signedIn(false)
    const wrapper = choice()

    expect(wrapper.find(CHOICE).exists()).toBe(false)
    expect(wrapper.get(IMPOSSIBLE).text()).toContain('Mailserver')
  })

  it('asks nothing about somebody who has no address of their own', () => {
    sessionInfo.value = signedIn(true)
    const wrapper = choice({hasAddress: false})

    expect(wrapper.find(CHOICE).exists()).toBe(false)
    expect(wrapper.find(IMPOSSIBLE).exists()).toBe(false)
  })

  it('stays silent for somebody with no address on an instance that cannot send either', () => {
    sessionInfo.value = signedIn(false)
    const wrapper = choice({hasAddress: false})

    expect(wrapper.find(CHOICE).exists()).toBe(false)
    expect(wrapper.find(IMPOSSIBLE).exists()).toBe(false)
  })

  it('carries the answer back out', async () => {
    sessionInfo.value = signedIn(true)
    const wrapper = choice()

    await wrapper.get(CHOICE).get('[role="switch"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false])
  })
})
