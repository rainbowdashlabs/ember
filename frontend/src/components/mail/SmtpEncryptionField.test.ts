/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import SmtpEncryptionField from './SmtpEncryptionField.vue'
import de from '@/i18n/de-DE'
import {SmtpEncryption} from '@/api/generated/schema'

/**
 * The choice of how a mail server is reached. Unencrypted is on offer, and says what it costs the
 * moment it is chosen.
 *
 * @vitest-environment happy-dom
 */
describe('SmtpEncryptionField', () => {
    const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': de, en: {}}})

    function field(modelValue: SmtpEncryption, port?: number) {
        return mount(SmtpEncryptionField, {props: {modelValue, port}, global: {plugins: [i18n]}})
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

    it('names the usual port for the chosen encryption', () => {
        expect(field(SmtpEncryption.STARTTLS, 587).get('[data-testid="smtp-usual-port"]').text()).toContain('587')
        expect(field(SmtpEncryption.IMPLICIT_TLS, 465).get('[data-testid="smtp-usual-port"]').text()).toContain('465')
        expect(field(SmtpEncryption.NONE, 25).find('[data-testid="smtp-usual-port"]').exists()).toBe(false)
    })

    it('points out a port that does not fit the encryption, and only then', () => {
        const unusual = field(SmtpEncryption.IMPLICIT_TLS, 587).get('[data-testid="smtp-unusual-port"]').text()
        expect(unusual).toContain('587')
        expect(unusual).toContain('465')

        expect(field(SmtpEncryption.STARTTLS, 587).find('[data-testid="smtp-unusual-port"]').exists()).toBe(false)
        expect(field(SmtpEncryption.STARTTLS).find('[data-testid="smtp-unusual-port"]').exists(), 'no port typed yet')
            .toBe(false)
        expect(field(SmtpEncryption.NONE, 2525).find('[data-testid="smtp-unusual-port"]').exists()).toBe(false)
    })
})
