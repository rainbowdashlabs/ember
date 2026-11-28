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

function detail(isOwner: boolean, status: LendingStatus, overdue: boolean, allowedSteps: LendingStatus[]): LendingRequestDetail {
    return {request: {allowedSteps, isOwner, overdue, itemSummary: '', owningStationName: 'A', requestingStationName: 'B', request: {status} as LendingRequestDetail['request']['request']}, items: []}
}

/** The buttons are the steps the server allows the viewing station, and nothing it does not. */
describe('LendingActionsBar', () => {
    it('offers the return check and, once overdue, a reminder to the lending station', async () => {
        const lender = mount(LendingActionsBar, {props: {detail: detail(true, 'LENT', true, ['RETURNED'])}, global: {plugins: [i18n]}})

        await lender.get('[data-testid="lending-start-return-check"]').trigger('click')
        await lender.get('[data-testid="lending-remind"]').trigger('click')
        expect(lender.emitted('checkReturn')).toHaveLength(1)
        expect(lender.emitted('remind')).toHaveLength(1)

        expect(mount(LendingActionsBar, {props: {detail: detail(true, 'LENT', false, ['RETURNED'])}, global: {plugins: [i18n]}})
            .find('[data-testid="lending-remind"]').exists()).toBe(false)
        const borrower = mount(LendingActionsBar, {props: {detail: detail(false, 'LENT', true, [])}, global: {plugins: [i18n]}})
        expect(borrower.find('[data-testid="lending-start-return-check"]').exists()).toBe(false)
        expect(borrower.find('[data-testid="lending-remind"]').exists()).toBe(false)
        expect(borrower.find('[data-testid="lending-decline"]').exists()).toBe(false)
    })

    it('lets the borrowing station withdraw an agreed request', async () => {
        const borrower = mount(LendingActionsBar, {props: {detail: detail(false, 'APPROVED', false, ['DECLINED'])}, global: {plugins: [i18n]}})

        await borrower.get('[data-testid="lending-decline"]').trigger('click')
        expect(borrower.emitted('decline')).toHaveLength(1)
        expect(borrower.text()).toContain('lending.withdraw')
    })
})
