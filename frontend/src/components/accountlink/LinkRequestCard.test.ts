/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import LinkRequestCard from './LinkRequestCard.vue'
import deDE from '@/i18n/de-DE'
import type {LinkPrompt} from '@/api/generated/schema'

function prompt(overrides: Partial<LinkPrompt> = {}): LinkPrompt {
  return {
    uid: '00000000-0000-0000-0000-000000000001',
    stationName: 'Wache Nord',
    memberName: 'Lena Weber',
    associationName: null,
    role: null,
    origin: 'INVITE',
    invitedBy: 'Jonas Becker',
    createdAt: '2026-10-08T09:00:00Z',
    expiresAt: '2026-11-07T09:00:00Z',
    ...overrides,
  }
}

/** One station's request to link the reader's account, as the prompt and the account page show it. */
describe('LinkRequestCard', () => {
  it('names the station, the member and who invited the address', () => {
    const card = mount(LinkRequestCard, {props: {prompt: prompt(), busy: false}})

    expect(card.text()).toContain('Wache Nord')
    expect(card.text()).toContain('Lena Weber')
    expect(card.text()).toContain('Jonas Becker')
  })

  it('says a member came along with a move rather than naming an inviter', () => {
    const card = mount(LinkRequestCard, {props: {prompt: prompt({origin: 'IMPORT', invitedBy: null}), busy: false}})

    expect(card.text()).toContain(deDE.accountLinks.fromImport)
  })

  it('names the association and the role it offers', () => {
    const card = mount(LinkRequestCard, {
      props: {
        prompt: prompt({
          stationName: null,
          memberName: null,
          invitedBy: null,
          associationName: 'Kreisverband Süd',
          role: 'CLUSTER_ADMIN',
          origin: 'ASSOCIATION_INVITE',
        }),
        busy: false,
      },
    })

    expect(card.text()).toContain('Kreisverband Süd')
    expect(card.text()).toContain(deDE.accountLinks.role.CLUSTER_ADMIN)
    expect(card.text()).toContain(deDE.accountLinks.fromAssociation)
  })

  it('hands the answer to whoever shows it', async () => {
    const card = mount(LinkRequestCard, {props: {prompt: prompt(), busy: false}})

    await card.find('[data-testid="link-accept"]').trigger('click')
    await card.find('[data-testid="link-decline"]').trigger('click')

    expect(card.emitted('accept')).toHaveLength(1)
    expect(card.emitted('decline')).toHaveLength(1)
  })
})
