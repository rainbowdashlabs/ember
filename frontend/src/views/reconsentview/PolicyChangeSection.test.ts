/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import PolicyChangeSection from './PolicyChangeSection.vue'

describe('PolicyChangeSection', () => {
  it('shows the changed lines rendered rather than as markdown source', () => {
    const section = mount(PolicyChangeSection, {
      props: {
        title: 'Privacy',
        diff: '@@ Line 3 @@\n- ## Old heading\n+ ## New heading\n+ We value **your** privacy.\n',
        addedKeyPrefix: 'a-',
        removedKeyPrefix: 'r-',
      },
    })

    expect(section.find('.text-error h2').text()).toBe('Old heading')
    expect(section.find('.text-success h2').text()).toBe('New heading')
    expect(section.find('.text-success strong').text()).toBe('your')
    expect(section.text()).not.toContain('##')
    expect(section.text()).not.toContain('**')
  })
})
