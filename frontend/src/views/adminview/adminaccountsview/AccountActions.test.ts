/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {flushPromises, mount} from '@vue/test-utils'
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest'
import type {AccountOverview, AccountSetupMail, SetupMailKind} from '@/api/generated/schema'
import {followModifierKeys} from '@/util/modifierKeys'
import AccountActions from './AccountActions.vue'

const sendSetupMail = vi.fn()
const resetSecondFactor = vi.fn()
const retirePassword = vi.fn()

vi.mock('@/api', () => ({
    adminAccounts: {
        sendSetupMail: (...args: unknown[]) => sendSetupMail(...args),
        issueOneTimePassword: vi.fn(),
    },
    adminSettings: {retirePassword: (...args: unknown[]) => retirePassword(...args)},
    twoFactorAdmin: {resetAccount2FAByInstanceAdmin: (...args: unknown[]) => resetSecondFactor(...args)},
}))

/** Presses and releases shift the way a reader holding it down would. */
function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

function account(setupMail: SetupMailKind | null): AccountOverview {
    return {
        id: 42, uid: '00000000-0000-0000-0000-000000000042', name: 'Lena Weber', email: 'lena@example.org',
        loginName: 'lena@example.org', instanceUserType: 'USER', lastSignInAt: null, stations: [], associations: [],
        hasPassword: false, passwordSignIn: false, oneTimePasswordExpiresAt: null, passkeys: 0, twoFactor: false,
        confirmed: false, setupMail,
    }
}

/**
 * The setup mail in the account's actions: offered only to an account that waits for one, worded by
 * the mail it is, held while it is on its way, and answered with what actually happened.
 *
 * @vitest-environment happy-dom
 */
describe('AccountActions', () => {
    const onChanged = vi.fn()

    function actions(setupMail: SetupMailKind | null) {
        return mount(AccountActions, {
            props: {account: account(setupMail), onChanged},
            global: {stubs: {OneTimePasswordAction: true, ConfirmDeleteModal: true}},
        })
    }

    beforeAll(() => followModifierKeys())
    beforeEach(() => {
        sendSetupMail.mockReset()
        resetSecondFactor.mockReset().mockResolvedValue(undefined)
        retirePassword.mockReset().mockResolvedValue(undefined)
        onChanged.mockReset()
    })
    afterEach(() => holdShift(false))

    it('offers the setup mail only to an account that waits for one', () => {
        expect(actions(null).find('[data-testid="account-setup-mail"]').exists()).toBe(false)
        expect(actions('INVITATION').find('[data-testid="account-setup-mail"]').text()).toBe('Einladung erneut senden')
        expect(actions('CONFIRMATION').find('[data-testid="account-setup-mail"]').text())
            .toBe('Bestätigungsmail erneut senden')
    })

    it('holds the button while the mail is on its way and says what went out', async () => {
        let answer: (sent: AccountSetupMail) => void = () => undefined
        sendSetupMail.mockReturnValue(new Promise(resolve => { answer = resolve }))
        const view = actions('INVITATION')

        await view.find('[data-testid="account-setup-mail"]').trigger('click')
        expect(view.find('[data-testid="account-setup-mail"]').attributes('disabled')).toBeDefined()
        answer({outcome: 'INVITATION_SENT'})
        await flushPromises()

        expect(sendSetupMail).toHaveBeenCalledWith(42)
        expect(view.text()).toContain('Die Einladung an Lena Weber ist verschickt.')
        expect(view.find('[data-testid="account-setup-mail"]').attributes('disabled')).toBeUndefined()
        expect(onChanged).toHaveBeenCalled()
    })

    it('says so when the address was marked confirmed instead of mailed', async () => {
        sendSetupMail.mockResolvedValue({outcome: 'CONFIRMED'})
        const view = actions('CONFIRMATION')

        await view.find('[data-testid="account-setup-mail"]').trigger('click')
        await flushPromises()

        expect(view.text()).toContain('Die Adresse von Lena Weber ist deshalb als bestätigt markiert.')
    })

    it('shows the refusal and no success', async () => {
        sendSetupMail.mockRejectedValue({
            response: {status: 400, data: {code: 'M-058', message: 'This account is already set up'}},
        })
        const view = actions('INVITATION')

        await view.find('[data-testid="account-setup-mail"]').trigger('click')
        await flushPromises()

        expect(view.text()).not.toContain('Die Einladung an Lena Weber ist verschickt.')
        expect(view.text()).toContain('Dieses Konto ist bereits eingerichtet')
        expect(onChanged).not.toHaveBeenCalled()
    })

    it('asks before resetting the second factor and resets once agreed', async () => {
        const view = actions(null)

        await view.find('[data-testid="account-reset-2fa"]').trigger('click')
        const question = view.findAllComponents({name: 'ConfirmDeleteModal'})[0]!
        expect(question.props('modelValue')).toBe(true)
        expect(resetSecondFactor).not.toHaveBeenCalled()

        question.vm.$emit('confirm')
        await flushPromises()

        expect(resetSecondFactor).toHaveBeenCalledWith(42)
        expect(onChanged).toHaveBeenCalled()
    })

    it('resets the second factor at once while shift is held', async () => {
        const view = actions(null)

        holdShift(true)
        await view.find('[data-testid="account-reset-2fa"]').trigger('click', {shiftKey: true})
        await flushPromises()

        expect(resetSecondFactor).toHaveBeenCalledWith(42)
        expect(view.findAllComponents({name: 'ConfirmDeleteModal'})[0]!.props('modelValue')).toBe(false)
        expect(onChanged).toHaveBeenCalled()
    })

    it('retires the password at once while shift is held', async () => {
        const view = actions(null)

        holdShift(true)
        await view.find('[data-testid="account-retire-password"]').trigger('click', {shiftKey: true})
        await flushPromises()

        expect(retirePassword).toHaveBeenCalledWith(42)
        expect(view.findAllComponents({name: 'ConfirmDeleteModal'})[1]!.props('modelValue')).toBe(false)
    })
})
