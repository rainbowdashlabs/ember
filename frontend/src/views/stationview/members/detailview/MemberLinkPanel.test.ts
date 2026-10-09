/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import type {LinkState} from '@/api/generated/schema'
import MemberLinkPanel from './MemberLinkPanel.vue'

const memberLink = vi.fn()
const sendAgain = vi.fn()

vi.mock('@/api', () => ({
  accountLinks: {
    memberLink: (...args: unknown[]) => memberLink(...args),
    sendAgain: (...args: unknown[]) => sendAgain(...args),
  },
}))

function state(overrides: Partial<LinkState> = {}): LinkState {
  return {
    status: 'WAITING',
    origin: 'INVITE',
    sentAt: '2026-10-01T09:00:00Z',
    expiresAt: '2026-10-31T09:00:00Z',
    answeredAt: null,
    sendAgainFrom: '2026-10-02T09:00:00Z',
    ...overrides,
  }
}

async function panel(canEdit = true) {
  const wrapper = mount(MemberLinkPanel, {props: {memberId: 7, canEdit}})
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
})

/** Where the request to link an existing account stands, on the member's own page. */
describe('MemberLinkPanel', () => {
  it('shows a waiting request and sends it again', async () => {
    memberLink.mockResolvedValue(state())
    sendAgain.mockResolvedValue(state({sendAgainFrom: '2999-01-01T00:00:00Z'}))
    const wrapper = await panel()

    expect(wrapper.find('[data-testid="member-link-waiting"]').exists()).toBe(true)
    await wrapper.find('[data-testid="member-link-send-again"]').trigger('click')
    await flushPromises()

    expect(sendAgain).toHaveBeenCalledWith(7)
    expect(wrapper.find('[data-testid="member-link-send-again"]').attributes('disabled')).toBeDefined()
  })

  it('offers nothing to send for a declined request', async () => {
    memberLink.mockResolvedValue(state({status: 'DECLINED', answeredAt: '2026-10-03T09:00:00Z', sendAgainFrom: null}))
    const wrapper = await panel()

    expect(wrapper.find('[data-testid="member-link-declined"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="member-link-send-again"]').exists()).toBe(false)
  })

  it('shows nothing where the station never asked, and no button to a reader', async () => {
    memberLink.mockResolvedValue(null)
    expect((await panel()).find('[data-testid="member-link"]').exists()).toBe(false)

    memberLink.mockResolvedValue(state({status: 'EXPIRED'}))
    const reader = await panel(false)
    expect(reader.find('[data-testid="member-link-expired"]').exists()).toBe(true)
    expect(reader.find('[data-testid="member-link-send-again"]').exists()).toBe(false)
  })
})
