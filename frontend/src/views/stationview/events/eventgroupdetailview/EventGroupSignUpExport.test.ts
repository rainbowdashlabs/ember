/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import EventGroupSignUpExport from './EventGroupSignUpExport.vue'
import RegistrationTableExport from '../eventdetailview/RegistrationTableExport.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'

const exportSignUps = vi.hoisted(() => vi.fn())
const presentFile = vi.hoisted(() => vi.fn())

vi.mock('@/api', () => ({eventGroups: {exportSignUps}}))
vi.mock('@/util/documentFile', () => ({presentFile}))

/** The sign-ups leave in the format and separator chosen, and a file that could not be made says so. */
describe('EventGroupSignUpExport', () => {
    beforeEach(() => {
        exportSignUps.mockReset()
        presentFile.mockReset()
    })

    function mountExport() {
        return mount(EventGroupSignUpExport, {
            props: {eventGroupId: 70},
            global: {stubs: {Modal: {template: '<div><slot/></div>'}}},
        })
    }

    it('hands the chosen file over', async () => {
        const file = {name: 'Anmeldungen.pdf'}
        exportSignUps.mockResolvedValue(file)
        const wrapper = mountExport()

        await wrapper.get('[data-testid="event-group-sign-ups-export"]').trigger('click')
        wrapper.getComponent(RegistrationTableExport).vm.$emit('download', 'pdf', 'comma')
        await flushPromises()

        expect(exportSignUps).toHaveBeenCalledWith(70, 'pdf', 'comma')
        expect(presentFile).toHaveBeenCalledWith(file)
    })

    it('says so when the file could not be made', async () => {
        exportSignUps.mockRejectedValue(new Error('kaputt'))
        const wrapper = mountExport()

        wrapper.getComponent(RegistrationTableExport).vm.$emit('download', 'csv', 'semicolon')
        await flushPromises()

        expect(presentFile).not.toHaveBeenCalled()
        expect(wrapper.findComponent(FailureAlert).exists()).toBe(true)
    })
})
