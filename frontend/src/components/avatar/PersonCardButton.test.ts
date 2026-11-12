/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount, type VueWrapper} from '@vue/test-utils'
import {h, ref} from 'vue'
import PersonCardButton from './PersonCardButton.vue'
import type {SessionInfo, SharedMemberCard} from '@/api/generated/schema'
import {createIdentity, createSessionInfo} from '@/test/mocks/factories'

const sessionInfo = ref<SessionInfo | null>(null)
const finePointer = ref(false)
const loadPersonCards = vi.fn<(accountUid: string) => Promise<SharedMemberCard[]>>()

vi.mock('@/composables/useSession', () => ({useSession: () => ({sessionInfo})}))
vi.mock('@/composables/useFinePointer', () => ({useFinePointer: () => ({finePointer})}))
vi.mock('@/composables/useMemberCards', () => ({
  loadMemberCard: vi.fn(),
  loadPersonCards: (accountUid: string) => loadPersonCards(accountUid),
  useMemberCardsShown: () => true,
}))

let wrapper: VueWrapper | null = null

function sharedAt(stationName: string, tag: string): SharedMemberCard {
  return {
    stationUid: `${stationName}-uid`,
    stationName,
    card: {
      identity: createIdentity({name: 'Ben Krause'}),
      name: 'Ben Krause',
      former: false,
      parents: [],
      children: [],
      tags: [{name: tag, color: null}],
      groups: [],
    },
  }
}

function mountFor(accountUid: string | null) {
  wrapper = mount(PersonCardButton, {
    props: {accountUid, name: 'Ben Krause'},
    slots: {default: () => h('span', 'Ben Krause')},
    attachTo: document.body,
    global: {stubs: {UserAvatar: true}},
  })
  return wrapper
}

beforeEach(() => {
  sessionInfo.value = createSessionInfo({stationId: null})
  finePointer.value = false
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
  loadPersonCards.mockReset()
})

describe('PersonCardButton', () => {
  it('opens the person\'s cards, one per shared station under its name', async () => {
    loadPersonCards.mockResolvedValue([sharedAt('Nord', 'Atemschutz'), sharedAt('Süd', 'Maschinist')])

    await mountFor('ben-account').get('[data-testid="person-card-trigger"]').trigger('click')
    await flushPromises()

    expect(loadPersonCards).toHaveBeenCalledWith('ben-account')
    const stations = document.body.querySelectorAll('[data-testid="shared-member-card"]')
    expect(stations).toHaveLength(2)
    expect(stations[0]!.textContent).toContain('Nord')
    expect(stations[0]!.textContent).toContain('Atemschutz')
    expect(stations[1]!.textContent).toContain('Maschinist')
  })

  it('opens on a mouse resting on the name, like a member\'s card', async () => {
    vi.useFakeTimers()
    finePointer.value = true
    loadPersonCards.mockResolvedValue([sharedAt('Nord', 'Atemschutz')])

    await mountFor('ben-account').trigger('mouseenter')
    expect(document.body.querySelector('[data-testid="member-card"]')).toBeNull()
    await vi.advanceTimersByTimeAsync(400)
    await flushPromises()

    expect(document.body.querySelector('[data-testid="shared-member-card"]')).not.toBeNull()
    vi.useRealTimers()
  })

  it('shows the content as it is for somebody without an account', () => {
    const shown = mountFor(null)

    expect(shown.find('[data-testid="person-card-trigger"]').exists()).toBe(false)
    expect(shown.text()).toContain('Ben Krause')
  })
})
