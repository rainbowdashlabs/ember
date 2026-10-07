/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import SignatureCell from './SignatureCell.vue'
import de from '@/i18n/de-DE'
import {SignatureRole} from '@/api/generated/schema'

/** A signature line in the editor names who signs on it and shows the text printed below. */
describe('SignatureCell', () => {
    const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': de, en: {}}})

    function shown(signer: SignatureRole | undefined, content: string): string {
        return mount(SignatureCell, {props: {config: {signer}, content}, global: {plugins: [i18n]}}).text()
    }

    it('names its signer and shows its text', () => {
        const text = shown(SignatureRole.EACH_GUARDIAN, 'Erziehungsberechtigte')

        expect(text).toContain(de.documentTemplates.signer.EACH_GUARDIAN)
        expect(text).toContain('Erziehungsberechtigte')
    })

    it('says so where nobody signs yet', () => {
        expect(shown(undefined, '')).toContain(de.documentTemplates.signerMissing)
    })
})
