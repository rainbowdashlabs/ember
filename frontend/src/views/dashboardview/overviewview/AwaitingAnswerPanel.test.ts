/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import AwaitingAnswerPanel from './AwaitingAnswerPanel.vue'

const listAwaitingAnswer = vi.fn(async () => [] as unknown[])
const listAwaiting = vi.fn(async () => [{
  courseId: 70, name: 'Jugendflamme 1', registrationDeadline: new Date(Date.now() + 2 * 86400000).toISOString(),
  members: [{memberId: 12, name: 'Mara'}],
}])

vi.mock('@/api', () => ({
  events: {listAwaitingAnswer: () => listAwaitingAnswer(), listCategories: async () => []},
  eventCourses: {listAwaiting: () => listAwaiting()},
}))

describe('AwaitingAnswerPanel', () => {
  it('lists a course still waiting on an answer, even when the appointments cannot load', async () => {
    listAwaitingAnswer.mockRejectedValueOnce(new Error('offline'))
    const panel = mount(AwaitingAnswerPanel)
    await flushPromises()

    const course = panel.find('[data-testid="awaiting-course"]')
    expect(course.exists()).toBe(true)
    expect(course.text()).toContain('Jugendflamme 1')
    expect(course.text()).toContain('Mara')
  })
})
