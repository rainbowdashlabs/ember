/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount, type VueWrapper} from '@vue/test-utils'
import {defineComponent, h, ref} from 'vue'
import MemberName from './MemberName.vue'
import type {MemberCard, SessionInfo} from '@/api/generated/schema'
import {createIdentity, createSessionInfo} from '@/test/mocks/factories'
import type {PersonIdentity} from '@/util/personIdentity'

const sessionInfo = ref<SessionInfo | null>(null)
const finePointer = ref(false)
const loadMemberCard = vi.fn<(memberUid: string) => Promise<MemberCard>>()

vi.mock('@/composables/useSession', () => ({useSession: () => ({sessionInfo})}))
vi.mock('@/composables/useFinePointer', () => ({useFinePointer: () => ({finePointer})}))
vi.mock('@/composables/useMemberCards', () => ({
  loadMemberCard: (memberUid: string) => loadMemberCard(memberUid),
  useMemberCardsShown: () => true,
}))

const TRIGGER = '[data-testid="member-card-trigger"]'

let wrapper: VueWrapper | null = null

function cardOf(identity: PersonIdentity): MemberCard {
  return {
    identity: createIdentity({memberUid: identity.memberUid, name: identity.name}),
    name: 'Maximilian "Max" Hoffmann',
    former: false,
    parents: [createIdentity({name: 'Petra Hoffmann'})],
    children: [],
    tags: [{name: 'Sanitäter', color: '#ff0000'}],
    groups: [{name: 'Jugend', color: null}],
  }
}

/** A row that opens something on a click, the way list rows and cards do. */
function mountInRow(identity: PersonIdentity, card = true) {
  const rowClicked = vi.fn()
  wrapper = mount(defineComponent(() => () =>
    h('div', {'data-testid': 'row', onClick: rowClicked}, [h(MemberName, {identity, card})])), {
    attachTo: document.body,
    global: {stubs: {UserAvatar: true}},
  })
  return {wrapper, rowClicked}
}

function card(): HTMLElement | null {
  return document.body.querySelector('[data-testid="member-card"]')
}

beforeEach(() => {
  sessionInfo.value = createSessionInfo({stationId: 'station-uid'})
  finePointer.value = false
  loadMemberCard.mockImplementation(memberUid => Promise.resolve(cardOf(createIdentity({memberUid}))))
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
  vi.useRealTimers()
  loadMemberCard.mockReset()
})

describe('MemberCardTrigger', () => {
  it('opens the card on a tap of the avatar without opening the row', async () => {
    const identity = createIdentity({name: 'Max Hoffmann'})
    const {rowClicked} = mountInRow(identity)

    await wrapper!.get(TRIGGER).trigger('click')
    await flushPromises()

    expect(rowClicked).not.toHaveBeenCalled()
    expect(loadMemberCard).toHaveBeenCalledWith(identity.memberUid)
    expect(card()?.textContent).toContain('Maximilian "Max" Hoffmann')
    expect(card()?.textContent).toContain('Petra Hoffmann')
    expect(card()?.textContent).toContain('Sanitäter')
    expect(card()?.textContent).toContain('Jugend')
  })

  it('closes on escape and gives focus back to the avatar without opening again', async () => {
    vi.useFakeTimers()
    mountInRow(createIdentity())
    await wrapper!.get(TRIGGER).trigger('click')
    await flushPromises()

    document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}))
    await vi.advanceTimersByTimeAsync(1000)

    expect(card()).toBeNull()
    expect(document.activeElement).toBe(wrapper!.get(TRIGGER).element)
  })

  it('opens when keyboard focus rests on the avatar', async () => {
    vi.useFakeTimers()
    mountInRow(createIdentity())

    ;(wrapper!.get(TRIGGER).element as HTMLElement).focus()
    await vi.advanceTimersByTimeAsync(400)
    await flushPromises()

    expect(card()).not.toBeNull()
  })

  it('opens on a mouse resting on the name, and not before', async () => {
    vi.useFakeTimers()
    finePointer.value = true
    mountInRow(createIdentity())

    await wrapper!.findComponent(MemberName).trigger('mouseenter')
    expect(card()).toBeNull()
    await vi.advanceTimersByTimeAsync(400)
    await flushPromises()

    expect(card()).not.toBeNull()
  })

  it('opens nothing on hover where there is no mouse', async () => {
    vi.useFakeTimers()
    mountInRow(createIdentity())

    await wrapper!.findComponent(MemberName).trigger('mouseenter')
    await vi.advanceTimersByTimeAsync(1000)

    expect(card()).toBeNull()
  })

  it('shows a member of another station without asking for their card', async () => {
    mountInRow(createIdentity({stationUid: 'elsewhere', name: 'Lea Brandt', stationName: 'South'}))

    await wrapper!.get(TRIGGER).trigger('click')
    await flushPromises()

    expect(loadMemberCard).not.toHaveBeenCalled()
    expect(card()?.textContent).toContain('Lea Brandt')
  })

  it('says so when the card could not be loaded', async () => {
    loadMemberCard.mockRejectedValue(new Error('offline'))
    mountInRow(createIdentity())

    await wrapper!.get(TRIGGER).trigger('click')
    await flushPromises()

    expect(card()?.textContent).toContain('Das Kurzprofil konnte nicht geladen werden.')
  })

  it('leaves the avatar alone where the card is turned off', async () => {
    const {rowClicked} = mountInRow(createIdentity(), false)

    expect(wrapper!.find(TRIGGER).exists()).toBe(false)
    await wrapper!.get('[data-testid="row"]').trigger('click')
    expect(rowClicked).toHaveBeenCalled()
  })
})
