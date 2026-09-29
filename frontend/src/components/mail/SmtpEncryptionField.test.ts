/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import SmtpEncryptionField from './SmtpEncryptionField.vue'
import de from '@/i18n/de-DE'
import {SmtpEncryption, type SmtpEncryptionName} from '@/api/mailProviders'

/**
 * The choice of how a mail server is reached. Unencrypted is on offer, and says what it costs the
 * moment it is chosen.
 */
describe('SmtpEncryptionField', () => {
    const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': de}})

    function field(modelValue: SmtpEncryptionName) {
        return mount(SmtpEncryptionField, {props: {modelValue}, global: {plugins: [i18n]}})
    }

    it('offers all three choices', () => {
        const values = field(SmtpEncryption.STARTTLS).findAll('option').map(option => option.attributes('value'))

        expect(values).toEqual([SmtpEncryption.STARTTLS, SmtpEncryption.IMPLICIT_TLS, SmtpEncryption.NONE])
    })

    it('warns when the connection is unencrypted', () => {
        expect(field(SmtpEncryption.NONE).text()).toContain(de.mailChain.encryption.noneWarning)
    })

    it('says nothing alarming for an encrypted connection', () => {
        expect(field(SmtpEncryption.STARTTLS).text()).not.toContain(de.mailChain.encryption.noneWarning)
        expect(field(SmtpEncryption.IMPLICIT_TLS).text()).not.toContain(de.mailChain.encryption.noneWarning)
    })

    it('hands the choice back', async () => {
        const wrapper = field(SmtpEncryption.STARTTLS)

        await wrapper.find('select').setValue(SmtpEncryption.NONE)

        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([SmtpEncryption.NONE])
    })
})
