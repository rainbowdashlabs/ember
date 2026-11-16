/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {ref} from 'vue'
import {afterEach, beforeEach, describe, expect, it} from 'vitest'
import {useEndFollowsStart} from './useEndFollowsStart'

const originalZone = process.env.TZ

beforeEach(() => {
    process.env.TZ = 'Europe/Berlin'
})
afterEach(() => {
    process.env.TZ = originalZone
})

/**
 * Changing only the start of an appointment used to leave its end behind, and the form then refused it
 * for ending before it begins until the end was changed as well.
 */
describe('useEndFollowsStart', () => {
    it('moves the end by as much as the start', () => {
        const start = ref('2026-11-16T18:00')
        const end = ref('2026-11-16T21:00')
        useEndFollowsStart(start, end)

        start.value = '2026-11-20T19:00'

        expect(end.value).toBe('2026-11-20T22:00')
    })

    it('keeps the hours of a night that had one more of them when winter time began', () => {
        const start = ref('2026-10-24T22:00')
        const end = ref('2026-10-25T06:00')
        useEndFollowsStart(start, end)

        start.value = '2026-10-23T22:00'

        expect(end.value).toBe('2026-10-24T07:00')
    })

    it('leaves an end alone that is set right after its start', () => {
        const start = ref('2026-11-16T18:00')
        const end = ref('2026-11-16T21:00')
        useEndFollowsStart(start, end)

        start.value = '2026-12-01T08:00'
        end.value = '2026-12-01T09:00'

        expect(end.value).toBe('2026-12-01T09:00')
    })

    it('moves nothing while there is no end yet', () => {
        const start = ref('2026-11-16T18:00')
        const end = ref('')
        useEndFollowsStart(start, end)

        start.value = '2026-11-20T18:00'

        expect(end.value).toBe('')
    })

    it('follows a year typed digit by digit from where it began', () => {
        const start = ref('2026-11-16T18:00')
        const end = ref('2026-11-16T21:00')
        useEndFollowsStart(start, end)

        for (const typed of ['0002', '0020', '0202', '2027']) start.value = `${typed}-11-16T18:00`

        expect(end.value).toBe('2027-11-16T21:00')
    })
})
