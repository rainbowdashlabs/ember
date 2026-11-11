/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {MonitoringCounts} from '@/api/generated/schema'
import {ADMIN_NAV} from '@/data/sidebar/adminNav'
import type {NavNode} from '@/data/sidebar/navTypes'
import {navPages, resolveNav} from '@/util/navTree'

function counts(overrides: Partial<MonitoringCounts> = {}): MonitoringCounts {
    return {problemLog: 0, problemReports: 0, beaconActive: false, beaconFaults: 0, beaconReports: 0, failedMails: 0, ...overrides}
}

function monitoring(waiting: MonitoringCounts): NavNode | undefined {
    return resolveNav(ADMIN_NAV, {counts: waiting, dev: false}, key => key).find(node => node.key === 'monitoring')
}

/**
 * The administration sidebar's Monitoring group: it counts what waits on its pages, so a closed
 * group still says that something does, and the mail log counts the mails that did not arrive.
 */
describe('ADMIN_NAV monitoring', () => {
    it('adds problems, reports and failed mails up on the group', () => {
        const group = monitoring(counts({problemLog: 2, problemReports: 3, failedMails: 4}))

        expect(group?.badge).toBe(9)
    })

    it('counts the failed mails on the mail log page', () => {
        const group = monitoring(counts({failedMails: 4}))
        const mailLog = navPages(group ? [group] : []).find(page => page.key === 'admin-mail-log')

        expect(mailLog?.badge).toBe(4)
    })

    it('shows nothing while nothing waits', () => {
        expect(monitoring(counts())?.badge).toBe(0)
    })
})
