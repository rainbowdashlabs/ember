/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount, type VueWrapper} from '@vue/test-utils'
import CoursePartnerSignUpsPanel from './CoursePartnerSignUpsPanel.vue'
import type {CourseDetail, CoursePartnerSignUp} from '@/api/generated/schema'
import {createIdentity} from '@/test/mocks/factories'

const listPartnerSignUps = vi.fn(async (..._args: unknown[]): Promise<CoursePartnerSignUp[]> => [])
const decidePartner = vi.fn(async (..._args: unknown[]) => undefined)

vi.mock('@/api', () => ({eventCourses: {
  listPartnerSignUps: (...args: unknown[]) => listPartnerSignUps(...args),
  decidePartner: (...args: unknown[]) => decidePartner(...args),
}}))

function detail(): CourseDetail {
  return {
    course: {
      id: 70, name: 'Jugendflamme 1', description: null, registrationLimit: null, registrationDeadline: null,
      requiresConfirmation: true, cancelled: false, cancelReason: null, partCount: 0, holding: 1,
    },
    sets: [],
    parts: [],
    registration: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    view: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    questions: [],
    household: [],
  }
}

let wrapper: VueWrapper | null = null

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  listPartnerSignUps.mockReset()
  decidePartner.mockClear()
})

describe('CoursePartnerSignUpsPanel', () => {
  it('shows nothing while no partner member signed up', async () => {
    listPartnerSignUps.mockResolvedValue([])
    wrapper = mount(CoursePartnerSignUpsPanel, {props: {detail: detail()}, global: {stubs: {MemberName: true}}})
    await flushPromises()

    expect(wrapper.find('[data-testid="course-partner-sign-ups"]').exists()).toBe(false)
  })

  it('accepts a partner member and reloads the page', async () => {
    listPartnerSignUps.mockResolvedValue([{
      id: 9, member: createIdentity({name: 'Mara'}), status: 'PENDING', registeredAt: '2026-11-13T10:00:00Z',
      parts: [11, 12], sets: [],
    }])
    const onChanged = vi.fn()
    wrapper = mount(CoursePartnerSignUpsPanel, {
      props: {detail: detail(), onChanged},
      global: {stubs: {MemberName: true}},
    })
    await flushPromises()

    expect(wrapper.findAll('[data-testid="course-partner-sign-up-row"]')).toHaveLength(1)
    await wrapper.find('[data-testid="course-partner-accept"]').trigger('click')
    await flushPromises()

    expect(decidePartner).toHaveBeenCalledWith(70, 9, 'ACCEPTED')
    expect(onChanged).toHaveBeenCalled()
  })
})
