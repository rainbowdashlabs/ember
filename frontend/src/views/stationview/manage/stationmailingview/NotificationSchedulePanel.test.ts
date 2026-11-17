/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import NotificationSchedulePanel from './NotificationSchedulePanel.vue'

const getNotificationSchedule = vi.fn()

vi.mock('@/api/mailProviders', async (original) => ({
    ...await original<typeof import('@/api/mailProviders')>(),
    getNotificationSchedule: () => getNotificationSchedule(),
    saveNotificationSchedule: vi.fn(),
}))

async function panelFor(floorMinutes: number) {
    getNotificationSchedule.mockResolvedValue({sendTimes: [], floorMinutes})
    const wrapper = await mountSuspended(NotificationSchedulePanel)
    await flushPromises()
    return wrapper.get('[data-testid="notification-schedule-operator"]').text()
}

/** Leaving the times to the instance says what the instance does, rather than only that it decides. */
describe('NotificationSchedulePanel', () => {
    it('names the instance interval in hours or minutes', async () => {
        expect(await panelFor(60)).toContain('einmal pro Stunde')
        expect(await panelFor(360)).toContain('alle 6 Stunden')
        expect(await panelFor(90)).toContain('alle 90 Minuten')
    })

    it('says so when the instance sends no gathered mail at all', async () => {
        expect(await panelFor(0)).toContain('keine gesammelten Benachrichtigungen')
    })
})
