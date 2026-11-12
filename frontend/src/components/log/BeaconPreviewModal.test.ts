/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import BeaconPreviewModal from './BeaconPreviewModal.vue'

const api = vi.hoisted(() => ({previewProblem: vi.fn(), sendProblem: vi.fn()}))

vi.mock('@/api', () => ({beacon: api}))

const SHOWN_AT = '2026-11-01T10:00:00Z'

function preview(withLines: boolean) {
    return {
        payload: {fingerprint: 'fp', context: withLines ? [{message: 'a line'}] : null},
        contextAt: withLines ? SHOWN_AT : null,
    }
}

/**
 * The log lines around a fault go only where the reader ticked them, and only once they were shown:
 * the preview is asked for again with the lines, sending waits for it, and the send names the
 * occurrence that preview showed.
 */
describe('BeaconPreviewModal', () => {
    beforeEach(() => {
        api.previewProblem.mockReset().mockImplementation((_id: number, withContext: boolean) =>
            Promise.resolve(preview(withContext)))
        api.sendProblem.mockReset().mockResolvedValue(1)
    })

    async function opened() {
        const wrapper = mount(BeaconPreviewModal, {
            props: {kind: 'problem', entryId: 5, open: false},
            global: {stubs: {Modal: {template: '<div><slot/></div>'}}},
        })
        await wrapper.setProps({open: true})
        await flushPromises()
        return wrapper
    }

    function sendButton(wrapper: Awaited<ReturnType<typeof opened>>) {
        return wrapper.findAll('button').find(button => button.text() === 'Senden')!
    }

    it('previews and sends without lines until they are ticked', async () => {
        const wrapper = await opened()

        expect(api.previewProblem).toHaveBeenLastCalledWith(5, false)
        await sendButton(wrapper).trigger('click')
        await flushPromises()

        expect(api.sendProblem).toHaveBeenLastCalledWith(5, null)
    })

    it('sends the lines of the occurrence the preview showed once ticked', async () => {
        const wrapper = await opened()

        await wrapper.find('input[data-testid="beacon-with-context"]').setValue(true)
        await flushPromises()
        expect(api.previewProblem).toHaveBeenLastCalledWith(5, true)
        expect(wrapper.text()).toContain('a line')
        await sendButton(wrapper).trigger('click')
        await flushPromises()

        expect(api.sendProblem).toHaveBeenLastCalledWith(5, SHOWN_AT)
    })

    it('does not send while the preview with lines is still on its way', async () => {
        const wrapper = await opened()
        api.previewProblem.mockImplementation(() => new Promise(() => {}))

        await wrapper.find('input[data-testid="beacon-with-context"]').setValue(true)
        await flushPromises()

        expect(sendButton(wrapper).attributes('disabled')).toBeDefined()
        expect(wrapper.text()).not.toContain('"fingerprint"')
    })

    it('says so where no lines are kept around the fault', async () => {
        api.previewProblem.mockResolvedValue({payload: {fingerprint: 'fp', context: null}, contextAt: null})
        const wrapper = await opened()

        await wrapper.find('input[data-testid="beacon-with-context"]').setValue(true)
        await flushPromises()

        expect(wrapper.find('[data-testid="beacon-no-lines-kept"]').exists()).toBe(true)
    })

    it('can be sent again after the queue was full', async () => {
        api.sendProblem.mockResolvedValueOnce(0)
        const wrapper = await opened()

        await sendButton(wrapper).trigger('click')
        await flushPromises()

        expect(sendButton(wrapper).attributes('disabled')).toBeUndefined()
    })
})
