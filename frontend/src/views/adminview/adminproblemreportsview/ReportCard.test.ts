/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import ReportCard from './ReportCard.vue'
import type {ProblemReport} from '@/api/problemReports'

/**
 * A report as the administration reads it.
 *
 * <p>What is pinned here is the difference between a report that has gone to the beacon, one that is
 * waiting for somebody here to send it, and one that is going nowhere. Those looked identical, and a
 * report being held then reads as a report being lost: the operator presses nothing because nothing
 * asks them to.
 */
describe('ReportCard', () => {
    const report: ProblemReport = {
        id: 1,
        stationId: '1',
        reporterName: 'Nora',
        message: 'Der Knopf tut nichts',
        acknowledged: false,
        createdAt: '2026-09-16T12:00:00Z',
    }

    function card(overrides: Partial<Parameters<typeof mount>[1] extends never ? never : Record<string, unknown>>) {
        return mount(ReportCard, {
            props: {report, expanded: false, canForward: true, ...overrides},
            global: {stubs: {AuthImage: true}},
        })
    }

    it('says when a report is waiting for somebody here', () => {
        const view = card({forwardState: 'held'})

        expect(view.find('[data-testid="problem-report-held"]').exists()).toBe(true)
        expect(view.find('[data-testid="problem-report-sent"]').exists()).toBe(false)
        expect(view.text()).toContain('Wartet auf dich')
    })

    it('says when a report has gone', () => {
        const view = card({forwardState: 'sent'})

        expect(view.find('[data-testid="problem-report-sent"]').exists()).toBe(true)
        expect(view.find('[data-testid="problem-report-held"]').exists()).toBe(false)
    })

    /** An instance that passes nothing on has nothing to say about it, so it says nothing. */
    it('says neither where the instance forwards nothing', () => {
        const view = card({forwardState: null})

        expect(view.find('[data-testid="problem-report-held"]').exists()).toBe(false)
        expect(view.find('[data-testid="problem-report-sent"]').exists()).toBe(false)
    })

    /** Whether it may be passed on is a different question from whether it has been. */
    it('still offers to pass on a report that is waiting', () => {
        const view = card({forwardState: 'held'})

        expect(view.find('[data-testid="problem-report-forward"]').exists()).toBe(true)
    })
})
