/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import EventGroupPartBadge from './EventGroupPartBadge.vue'

const eventGroup = {eventGroupId: 70, eventGroupName: 'Jugendflamme 1', setId: 1, setName: 'Übung', number: 2, count: 4, color: null}

describe('EventGroupPartBadge', () => {
  it('names the event group and where the part stands in it', () => {
    const badge = mount(EventGroupPartBadge, {props: {eventGroup}})

    expect(badge.text()).toContain('Jugendflamme 1, Teil 2 von 4')
  })

})
