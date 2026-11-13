/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import {ownCoursePort, partnerCoursePort} from './courseSignUpPort'

const {eventCourses, partnerCourses} = vi.hoisted(() => ({
  eventCourses: {signUp: vi.fn(), changeParts: vi.fn(), withdraw: vi.fn()},
  partnerCourses: {signUp: vi.fn(), changeParts: vi.fn(), withdraw: vi.fn()},
}))

vi.mock('@/api', () => ({eventCourses, partnerCourses}))

describe('courseSignUpPort', () => {
  it('sends a sign-up for a course of this station with its answers', async () => {
    await ownCoursePort(70).signUp([11], [{fieldId: 5, value: 'M'}], 12)

    expect(eventCourses.signUp).toHaveBeenCalledWith(70, [11], [{fieldId: 5, value: 'M'}], 12)
  })

  it('sends a sign-up for a partner course to that partner, without answers', async () => {
    const port = partnerCoursePort('station-b', 70)
    await port.signUp([11, 12], [{fieldId: 5, value: 'M'}], 12)
    await port.changeParts([12], 12)
    await port.withdraw(12)

    expect(partnerCourses.signUp).toHaveBeenCalledWith('station-b', 70, [11, 12], 12)
    expect(partnerCourses.changeParts).toHaveBeenCalledWith('station-b', 70, [12], 12)
    expect(partnerCourses.withdraw).toHaveBeenCalledWith('station-b', 70, 12)
  })
})
