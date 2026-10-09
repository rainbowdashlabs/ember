/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {ref} from 'vue'
import CreateView from './CreateView.vue'
import StepDispatcher from './createview/StepDispatcher.vue'
import type {IssuedOneTimePassword, SessionInfo} from '@/api/generated/schema'
import {StationPermission} from '@/api/generated/schema'
import {createSessionInfo} from '@/test/mocks/factories'

const api = vi.hoisted(() => ({
    members: {invite: vi.fn(), issueOneTimePassword: vi.fn()},
    stationMembers: {listMembers: vi.fn(), setUserType: vi.fn(), setManagers: vi.fn()},
    profileFields: {listFields: vi.fn(), setValues: vi.fn()},
    memberGroups: {listGroups: vi.fn(), setMemberGroups: vi.fn()},
}))
vi.mock('@/api', () => api)

const sessionInfo = ref<SessionInfo | null>(null)
const held = ref<string[]>([])
vi.mock('@/composables/useSession', () => ({
    useSession: () => ({sessionInfo, hasPermission: (permission: string) => held.value.includes(permission)}),
}))
vi.mock('@/composables/useFieldAudiences', () => ({
    useFieldAudiences: () => ({load: async () => undefined, fieldsFor: () => []}),
}))
vi.mock('vue-router', () => ({useRouter: () => ({push: vi.fn()})}))

const ISSUED: IssuedOneTimePassword = {
    accountId: 7, name: 'Lena Weber', loginName: 'lena@example.org',
    password: 'k7mq-x2pd-9wtr-hb4z', expiresAt: '2026-10-09T12:00:00Z',
}

async function wizard() {
    const wrapper = mount(CreateView, {
        global: {stubs: {ViewContent: {template: '<div><slot/></div>'}, StepDispatcher: true}},
    })
    await flushPromises()
    return wrapper.findComponent(StepDispatcher)
}

/** Fills in a new member with an address and a login, the way the identity step does, and creates them. */
async function createLena(dispatcher: Awaited<ReturnType<typeof wizard>>) {
    dispatcher.vm.$emit('update:firstName', 'Lena')
    dispatcher.vm.$emit('update:lastName', 'Weber')
    dispatcher.vm.$emit('update:email', 'lena@example.org')
    dispatcher.vm.$emit('update:selectedUserType', 'TRIAL')
    dispatcher.vm.$emit('create-account')
    await flushPromises()
}

beforeEach(() => {
    vi.clearAllMocks()
    api.members.invite.mockResolvedValue({
        memberId: 70, id: 7, email: 'lena@example.org', firstName: 'Lena', lastName: 'Weber', linkPending: false,
    })
    api.members.issueOneTimePassword.mockResolvedValue(ISSUED)
    api.stationMembers.listMembers.mockResolvedValue([{id: 70, accountId: 7}])
    api.profileFields.listFields.mockResolvedValue([])
    api.memberGroups.listGroups.mockResolvedValue([])
    sessionInfo.value = createSessionInfo({canSendMail: false})
    held.value = [StationPermission.STATION_ADMINISTRATOR]
})

describe('CreateView without a mail server', () => {
    it('offers the station administration a one-time password', async () => {
        const dispatcher = await wizard()

        expect(dispatcher.props('offerOneTimePassword')).toBe(true)
        expect(dispatcher.props('issueOneTimePassword')).toBe(true)
    })

    it('hands the new member a one-time password instead of queueing a setup mail', async () => {
        const dispatcher = await wizard()

        await createLena(dispatcher)

        expect(api.members.invite).toHaveBeenCalledWith(expect.objectContaining({sendSetupMail: false}))
        expect(api.members.issueOneTimePassword).toHaveBeenCalledWith(7)
        expect(dispatcher.props('step')).toBe('done')
        expect(dispatcher.props('oneTimePassword')).toEqual(ISSUED)
    })

    it('keeps the member and names the refusal when no password may be issued', async () => {
        api.members.issueOneTimePassword.mockRejectedValue(new Error('refused'))
        const dispatcher = await wizard()

        await createLena(dispatcher)

        expect(dispatcher.props('step')).toBe('done')
        expect(dispatcher.props('oneTimePassword')).toBeNull()
        expect(dispatcher.props('oneTimePasswordFailure')).not.toBeNull()
    })

    it('issues nothing when the administration turns it off', async () => {
        const dispatcher = await wizard()
        dispatcher.vm.$emit('update:issueOneTimePassword', false)

        await createLena(dispatcher)

        expect(api.members.issueOneTimePassword).not.toHaveBeenCalled()
    })

    it('offers nothing to somebody who may not issue one', async () => {
        held.value = [StationPermission.MEMBER_EDIT]
        const dispatcher = await wizard()

        await createLena(dispatcher)

        expect(dispatcher.props('offerOneTimePassword')).toBe(false)
        expect(api.members.issueOneTimePassword).not.toHaveBeenCalled()
    })

    it('offers nothing where mail goes out', async () => {
        sessionInfo.value = createSessionInfo({canSendMail: true})
        const dispatcher = await wizard()

        await createLena(dispatcher)

        expect(dispatcher.props('offerOneTimePassword')).toBe(false)
        expect(api.members.invite).toHaveBeenCalledWith(expect.objectContaining({sendSetupMail: true}))
    })
})
