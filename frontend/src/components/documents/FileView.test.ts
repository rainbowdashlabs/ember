/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import FileView from './FileView.vue'

const get = vi.fn()

vi.mock('@/api/client', () => ({default: {get: (url: string, config: unknown) => get(url, config)}}))

/**
 * A file read where it is, which for a PDF is the only way it can be read on a phone at all.
 *
 * <p>Handing the bytes over directly is what lets an export be looked at the moment it is built,
 * without a round trip to fetch back what the page is already holding.
 */

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    missingWarn: false,
    fallbackWarn: false,
    messages: {'de-DE': {}},
})

const stubs = {
    PdfCanvas: {name: 'PdfCanvas', props: ['source', 'page'], template: '<canvas data-testid="pdf-canvas"/>'},
}

function open(source: string | Blob, mimeType: string) {
    return mount(FileView, {
        props: {source, title: 'Bericht', mimeType},
        global: {plugins: [i18n], stubs},
    })
}

describe('FileView', () => {
    beforeEach(() => {
        get.mockReset()
        URL.createObjectURL = vi.fn(() => 'blob:shown')
        URL.revokeObjectURL = vi.fn()
    })

    it('asks the endpoint for the bytes when given an address', async () => {
        get.mockResolvedValue({data: new Blob(['x'], {type: 'image/png'})})

        open('/events/1/attachments/2', 'image/png')
        await flushPromises()

        expect(get).toHaveBeenCalledWith('/events/1/attachments/2', {responseType: 'blob'})
    })

    /** An export is already in the page's hands, so fetching it back would be a round trip for nothing. */
    it('uses bytes it is handed without asking for them again', async () => {
        const view = open(new Blob(['%PDF'], {type: 'application/pdf'}), 'application/pdf')
        await flushPromises()

        expect(get).not.toHaveBeenCalled()
        expect(view.get('[data-testid="pdf-canvas"]')).toBeTruthy()
    })

    /**
     * A phone has no viewer behind a frame, so a PDF drawn that way is an empty box. The pages are
     * drawn onto a canvas instead.
     */
    it('draws a PDF on the canvas rather than in a frame', async () => {
        const view = open(new Blob(['%PDF'], {type: 'application/pdf'}), 'application/pdf')
        await flushPromises()

        expect(view.find('iframe').exists()).toBe(false)
        expect(view.find('[data-testid="pdf-canvas"]').exists()).toBe(true)
    })

    it('offers the pages only once there is more than one', async () => {
        const view = open(new Blob(['%PDF'], {type: 'application/pdf'}), 'application/pdf')
        await flushPromises()
        expect(view.find('[data-testid="file-view-pages"]').exists()).toBe(false)

        view.findComponent({name: 'PdfCanvas'}).vm.$emit('loaded', 3)
        await flushPromises()

        expect(view.get('[data-testid="file-view-pages"]').text()).toContain('1 / 3')
    })

    it('turns to the page the reader asks for and stops at the last', async () => {
        const view = open(new Blob(['%PDF'], {type: 'application/pdf'}), 'application/pdf')
        await flushPromises()
        view.findComponent({name: 'PdfCanvas'}).vm.$emit('loaded', 2)
        await flushPromises()

        const [, next] = view.findAll('[data-testid="file-view-pages"] button')
        await next!.trigger('click')
        expect(view.get('[data-testid="file-view-pages"]').text()).toContain('2 / 2')

        expect((next!.element as HTMLButtonElement).disabled).toBe(true)
    })

    it('plays what it cannot draw but can sound', async () => {
        const view = open(new Blob(['mp3'], {type: 'audio/mpeg'}), 'audio/mpeg')
        await flushPromises()

        expect(view.find('audio').exists()).toBe(true)
    })

    it('says so when the bytes cannot be had', async () => {
        get.mockRejectedValue(new Error('refused'))

        const view = open('/events/1/attachments/2', 'image/png')
        await flushPromises()

        expect(view.find('[data-testid="pdf-canvas"]').exists()).toBe(false)
        expect(view.text()).toContain('failure.UNKNOWN.message')
    })

    it('never asks for a kind it could not draw anyway', async () => {
        const view = open('/documents/3/content', 'application/zip')
        await flushPromises()

        expect(get).not.toHaveBeenCalled()
        expect(view.text()).toContain('files.noPreview')
    })
})
