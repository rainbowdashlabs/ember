/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import deDE from '@/i18n/de-DE'
import type {MemberCard, MessagePersonResponse} from '@/api/generated/schema'
import {createIdentity} from '@/test/mocks/factories'
import MemberProfileCard from './MemberProfileCard.vue'

const BEN: MessagePersonResponse = {uid: 'ben', firstName: 'Ben', lastName: 'Muster'}

function card(former = false): MemberCard {
  return {identity: createIdentity({name: 'Ben Muster'}), name: 'Ben Muster', former, parents: [], children: [], tags: [], groups: []}
}

function profile(props: Record<string, unknown>) {
  return mount(MemberProfileCard, {
    props: {identity: createIdentity({name: 'Ben Muster'}), name: 'Ben Muster', card: null, state: 'loading', ...props},
    global: {
      plugins: [createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}})],
      stubs: {UserAvatar: true, MemberCardDetails: true, MemberMessageButton: {template: '<span data-testid="message-button"/>'}},
    },
  })
}

describe('the message button on a member card', () => {
  it('shows once the card is ready and the reader may write to the member', () => {
    expect(profile({card: card(), state: 'ready', writable: BEN}).find('[data-testid="message-button"]').exists()).toBe(true)
  })

  it('is not there before the card is ready, nor where nobody may be written to', () => {
    expect(profile({writable: BEN}).find('[data-testid="message-button"]').exists()).toBe(false)
    expect(profile({card: card(), state: 'ready'}).find('[data-testid="message-button"]').exists()).toBe(false)
  })
})
