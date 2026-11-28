/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import {describe, expect, it} from 'vitest'
import type {LendingRequestDetail, LendingStatus} from '@/api/generated/schema'
import LendingActionsBar from './LendingActionsBar.vue'

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}, missingWarn: false, fallbackWarn: false})

function detail(isOwner: boolean, status: LendingStatus, overdue: boolean): LendingRequestDetail {
    return {request: {isOwner, overdue, itemSummary: '', owningStationName: 'A', requestingStationName: 'B', request: {status} as LendingRequestDetail['request']['request']}, items: []}
}

/** The lending station checks lent gear back and reminds of overdue gear; the borrowing station marks it returned. */
describe('LendingActionsBar', () => {
    it('offers the return check and, once overdue, a reminder to the lending station', async () => {
        const lender = mount(LendingActionsBar, {props: {detail: detail(true, 'LENT', true)}, global: {plugins: [i18n]}})

        await lender.get('[data-testid="lending-start-return-check"]').trigger('click')
        await lender.get('[data-testid="lending-remind"]').trigger('click')
        expect(lender.emitted('checkReturn')).toHaveLength(1)
        expect(lender.emitted('remind')).toHaveLength(1)

        expect(mount(LendingActionsBar, {props: {detail: detail(true, 'LENT', false)}, global: {plugins: [i18n]}})
            .find('[data-testid="lending-remind"]').exists()).toBe(false)
        const borrower = mount(LendingActionsBar, {props: {detail: detail(false, 'LENT', true)}, global: {plugins: [i18n]}})
        expect(borrower.find('[data-testid="lending-start-return-check"]').exists()).toBe(false)
        expect(borrower.find('[data-testid="lending-remind"]').exists()).toBe(false)
    })
})
