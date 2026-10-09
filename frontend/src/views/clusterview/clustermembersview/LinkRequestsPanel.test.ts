/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import type {AssociationLinkState} from '@/api/generated/schema'
import LinkRequestsPanel from './LinkRequestsPanel.vue'

const sendAssociationRequestAgain = vi.fn()

vi.mock('@/api', () => ({
  accountLinks: {
    sendAssociationRequestAgain: (...args: unknown[]) => sendAssociationRequestAgain(...args),
  },
}))

function request(overrides: Partial<AssociationLinkState> = {}): AssociationLinkState {
  return {
    uid: '00000000-0000-0000-0000-000000000001',
    address: 'lena@example.test',
    role: 'CLUSTER_USER',
    status: 'WAITING',
    sentAt: '2026-10-01T09:00:00Z',
    expiresAt: '2026-10-31T09:00:00Z',
    answeredAt: null,
    sendAgainFrom: '2026-10-02T09:00:00Z',
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
})

/** The association's requests to addresses that already have an account, on its team page. */
describe('LinkRequestsPanel', () => {
  it('names the address and sends a waiting request again', async () => {
    sendAssociationRequestAgain.mockResolvedValue(request())
    const wrapper = mount(LinkRequestsPanel, {props: {requests: [request()], editable: true}})

    expect(wrapper.text()).toContain('lena@example.test')
    expect(wrapper.find('[data-testid="member-link-waiting"]').exists()).toBe(true)
    await wrapper.find('[data-testid="association-link-send-again"]').trigger('click')
    await flushPromises()

    expect(sendAssociationRequestAgain).toHaveBeenCalledWith(request().uid)
    expect(wrapper.emitted('changed')).toHaveLength(1)
  })

  it('holds a request back until a day has passed', () => {
    const wrapper = mount(LinkRequestsPanel, {
      props: {requests: [request({sendAgainFrom: '2999-01-01T00:00:00Z'})], editable: true},
    })

    expect(wrapper.find('[data-testid="association-link-send-again"]').attributes('disabled')).toBeDefined()
  })

  it('offers nothing to send for a declined request, and no button to a reader', () => {
    const declined = request({status: 'DECLINED', answeredAt: '2026-10-03T09:00:00Z', sendAgainFrom: null})
    const wrapper = mount(LinkRequestsPanel, {props: {requests: [declined], editable: true}})
    expect(wrapper.find('[data-testid="member-link-declined"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="association-link-send-again"]').exists()).toBe(false)

    const reader = mount(LinkRequestsPanel, {props: {requests: [request({status: 'EXPIRED'})], editable: false}})
    expect(reader.find('[data-testid="association-link-send-again"]').exists()).toBe(false)
  })

  it('shows nothing where the association asked nobody', () => {
    const wrapper = mount(LinkRequestsPanel, {props: {requests: [], editable: true}})

    expect(wrapper.find('[data-testid="association-link-requests"]').exists()).toBe(false)
  })
})
