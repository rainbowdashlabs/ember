/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import type {MemberCard} from '@/api/generated/schema'
import {createIdentity} from '@/test/mocks/factories'

const getMemberCard = vi.fn<(memberUid: string) => Promise<MemberCard>>()

vi.mock('@/api/members', () => ({getMemberCard: (memberUid: string) => getMemberCard(memberUid)}))

const {loadMemberCard} = await import('./useMemberCards')

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
})
