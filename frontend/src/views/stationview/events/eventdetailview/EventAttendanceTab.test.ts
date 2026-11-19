/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import EventAttendanceTab from './EventAttendanceTab.vue'

/** The attendance of the date on screen has a tab of its own, which opens the list kept or starts one. */
describe('EventAttendanceTab', () => {
  it('starts taking attendance where no list is kept yet', async () => {
    const tab = await mountSuspended(EventAttendanceTab, {props: {attendanceSessionId: null}})

    expect(tab.text()).toContain('Anwesenheit erfassen')
    await tab.get('[data-testid="event-attendance-tab-open"]').trigger('click')
    expect(tab.emitted('attendance')).toHaveLength(1)
  })

  it('opens the list already kept for the date', async () => {
    const tab = await mountSuspended(EventAttendanceTab, {props: {attendanceSessionId: 5}})

    expect(tab.text()).toContain('Erfassung öffnen')
    expect(tab.text()).toContain('gibt es eine Anwesenheitsliste')
  })
})
