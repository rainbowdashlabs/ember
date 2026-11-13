/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import {ownEventGroupPort, partnerEventGroupPort} from './eventGroupSignUpPort'

const {eventGroups, partnerEventGroups} = vi.hoisted(() => ({
  eventGroups: {signUp: vi.fn(), changeParts: vi.fn(), withdraw: vi.fn()},
  partnerEventGroups: {signUp: vi.fn(), changeParts: vi.fn(), withdraw: vi.fn()},
}))

vi.mock('@/api', () => ({eventGroups, partnerEventGroups}))

describe('eventGroupSignUpPort', () => {
  it('sends a sign-up for an event group of this station with its answers', async () => {
    await ownEventGroupPort(70).signUp([11], [{fieldId: 5, value: 'M'}], 12)

    expect(eventGroups.signUp).toHaveBeenCalledWith(70, [11], [{fieldId: 5, value: 'M'}], 12)
  })

  it('sends a sign-up for a partner event group to that partner, without answers', async () => {
    const port = partnerEventGroupPort('station-b', 70)
    await port.signUp([11, 12], [{fieldId: 5, value: 'M'}], 12)
    await port.changeParts([12], 12)
    await port.withdraw(12)

    expect(partnerEventGroups.signUp).toHaveBeenCalledWith('station-b', 70, [11, 12], 12)
    expect(partnerEventGroups.changeParts).toHaveBeenCalledWith('station-b', 70, [12], 12)
    expect(partnerEventGroups.withdraw).toHaveBeenCalledWith('station-b', 70, 12)
  })
})
