/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount, type VueWrapper} from '@vue/test-utils'
import {ref} from 'vue'
import CommentThread from './CommentThread.vue'
import type {CommentResponse, MemberCard, MemberCompletion, SessionInfo} from '@/api/generated/schema'
import {createIdentity, createSessionInfo} from '@/test/mocks/factories'

const sessionInfo = ref<SessionInfo | null>(createSessionInfo({stationId: 'station-uid'}))
const loadMemberCard = vi.fn<(memberUid: string) => Promise<MemberCard>>()

vi.mock('@/composables/useSession', () => ({useSession: () => ({sessionInfo, hasPermission: () => false})}))
vi.mock('@/composables/useMemberCards', () => ({
  loadMemberCard: (memberUid: string) => loadMemberCard(memberUid),
  useMemberCardsShown: () => true,
}))

let wrapper: VueWrapper | null = null

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
  loadMemberCard.mockReset()
})

const anna: MemberCompletion = {
  id: 7,
  memberUid: 'member-anna',
  stationUid: 'station-uid',
  name: 'Anna Alt',
  stationName: null,
  nameColor: null,
  displayTag: null,
}

function threadSaying(content: string) {
  const comment: CommentResponse = {
    id: 1,
    content,
    createdAt: '2026-10-01T10:00:00Z',
    deleted: false,
    author: createIdentity({name: 'Max Hoffmann'}),
  }
  wrapper = mount(CommentThread, {
    attachTo: document.body,
    props: {comments: [comment], members: [anna], readonly: true},
    global: {stubs: {UserAvatar: true, MentionInput: true}},
  })
  return wrapper
}

function mentions() {
  return wrapper!.findAll('[data-testid="mention"]')
}

describe('a mention in a comment', () => {
  it('opens the card of the member it names', async () => {
    loadMemberCard.mockResolvedValue({
      identity: createIdentity({memberUid: 'member-max'}),
      name: 'Max Hoffmann',
      former: false,
      parents: [],
      children: [],
      tags: [],
      groups: [],
    })
    threadSaying('Danke @[station-uid/member-max:Max]')

    await mentions()[0]!.trigger('click')
    await flushPromises()

    expect(loadMemberCard).toHaveBeenCalledWith('member-max')
    expect(document.body.querySelector('[data-testid="member-card"]')).not.toBeNull()
  })

  it('finds the member of an older mention written by number', () => {
    threadSaying('Frag @[7:Anna]')

    expect(mentions()[0]!.element.tagName).toBe('BUTTON')
    expect(mentions()[0]!.text()).toBe('@Anna Alt')
  })

  it('leaves a mention of a whole group and of somebody unknown as text', () => {
    threadSaying('An @[GROUP:Vorstand:5] und @[99:Weg]')

    expect(mentions().map(mention => mention.element.tagName)).toEqual(['SPAN', 'SPAN'])
  })
})
