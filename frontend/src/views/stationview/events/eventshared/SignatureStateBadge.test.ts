/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {RequirementSignatureState} from '@/api/generated/schema'
import SignatureStateBadge from './SignatureStateBadge.vue'

/** The issuer's field as participants see it: signed by the station, in a neutral badge rather than as open. */
describe('SignatureStateBadge', () => {
    it('shows the station\'s field neutrally', async () => {
        const badge = await mountSuspended(SignatureStateBadge, {props: {state: RequirementSignatureState.BY_STATION}})

        expect(badge.text()).toBe('Wird von der Wache unterschrieben')
        expect(badge.find('[data-testid="signature-by-station"]').exists()).toBe(true)
    })

    it('shows an open field as open', async () => {
        const badge = await mountSuspended(SignatureStateBadge, {props: {state: RequirementSignatureState.OPEN}})

        expect(badge.text()).toBe('Unterschrift offen')
    })
})
