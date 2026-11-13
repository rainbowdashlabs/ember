/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import type {MemberCard, SharedMemberCard} from '@/api/generated/schema'
import {createIdentity, createSessionInfo} from '@/test/mocks/factories'
import {sessionWriter} from '@/util/sessionState'

const getMemberCard = vi.fn<(memberUid: string) => Promise<MemberCard>>()

vi.mock('@/api/members', () => ({getMemberCard: (memberUid: string) => getMemberCard(memberUid)}))

const getPersonCards = vi.fn<(accountUid: string) => Promise<SharedMemberCard[]>>()

vi.mock('@/api/messages', () => ({getPersonCards: (accountUid: string) => getPersonCards(accountUid)}))

const {loadMemberCard, loadPersonCards} = await import('./useMemberCards')

function cardFor(memberUid: string): MemberCard {
  return {
    identity: createIdentity({memberUid}),
    name: 'Anna',
    former: false,
    parents: [],
    children: [],
    tags: [],
    groups: [],
  }
}

describe('loadMemberCard', () => {
  it('asks the server once per member however often the card is opened', async () => {
    getMemberCard.mockImplementation(uid => Promise.resolve(cardFor(uid)))

    await loadMemberCard('asked-once')
    await loadMemberCard('asked-once')

    expect(getMemberCard).toHaveBeenCalledTimes(1)
  })

  it('asks again after a request that failed', async () => {
    getMemberCard.mockReset()
    getMemberCard.mockRejectedValueOnce(new Error('offline'))
    getMemberCard.mockImplementation(uid => Promise.resolve(cardFor(uid)))

    await expect(loadMemberCard('failed-first')).rejects.toThrow('offline')
    await expect(loadMemberCard('failed-first')).resolves.toMatchObject({name: 'Anna'})
    expect(getMemberCard).toHaveBeenCalledTimes(2)
  })

  it('asks again for somebody else, so one viewer never sees the card another one was shown', async () => {
    getMemberCard.mockReset()
    getMemberCard.mockImplementation(uid => Promise.resolve(cardFor(uid)))
    sessionWriter().setInfo(createSessionInfo({account: {id: 1}, permissions: ['MEMBER_READ']}))
    await loadMemberCard('seen-by-two')

    sessionWriter().setInfo(createSessionInfo({account: {id: 2}, permissions: []}))
    await loadMemberCard('seen-by-two')

    expect(getMemberCard).toHaveBeenCalledTimes(2)
  })

  it('asks again once the viewer\'s permissions change', async () => {
    getMemberCard.mockReset()
    getMemberCard.mockImplementation(uid => Promise.resolve(cardFor(uid)))
    sessionWriter().setInfo(createSessionInfo({account: {id: 3}, permissions: ['MEMBER_READ']}))
    await loadMemberCard('permission-change')

    sessionWriter().setInfo(createSessionInfo({account: {id: 3}, permissions: []}))
    await loadMemberCard('permission-change')

    expect(getMemberCard).toHaveBeenCalledTimes(2)
  })

  it('asks for a person\'s cards once per viewer, like a member\'s', async () => {
    getPersonCards.mockReset()
    getPersonCards.mockResolvedValue([])
    sessionWriter().setInfo(createSessionInfo({account: {id: 4}, stationId: null}))

    await loadPersonCards('ben-account')
    await loadPersonCards('ben-account')
    sessionWriter().setInfo(createSessionInfo({account: {id: 5}, stationId: null}))
    await loadPersonCards('ben-account')

    expect(getPersonCards).toHaveBeenCalledTimes(2)
  })
})
