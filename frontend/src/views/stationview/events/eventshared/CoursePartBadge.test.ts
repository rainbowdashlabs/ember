/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import CoursePartBadge from './CoursePartBadge.vue'

const course = {courseId: 70, courseName: 'Jugendflamme 1', setId: 1, setName: 'Übung', number: 2, count: 4}

describe('CoursePartBadge', () => {
  it('names the course and where the part stands in it', () => {
    const badge = mount(CoursePartBadge, {props: {course}})

    expect(badge.text()).toContain('Jugendflamme 1, Teil 2 von 4')
  })

  it('links to the course only where it stands on its own', () => {
    expect(mount(CoursePartBadge, {props: {course}}).find('a').exists()).toBe(false)
    expect(mount(CoursePartBadge, {props: {course, link: true}}).find('a').exists()).toBe(true)
  })
})
