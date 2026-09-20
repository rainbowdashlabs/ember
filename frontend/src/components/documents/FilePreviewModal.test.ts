/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import FilePreviewModal from './FilePreviewModal.vue'

/**
 * The frame around a file, which is all this is: the drawing belongs to {@link FileView} and is
 * held by its own tests.
 */

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    missingWarn: false,
    fallbackWarn: false,
    messages: {'de-DE': {}},
})

const stubs = {
    Modal: {template: '<div><slot/></div>'},
    FileView: {name: 'FileView', props: ['source', 'title', 'mimeType'], template: '<div/>'},
}

function open() {
    return mount(FilePreviewModal, {
        props: {source: '/documents/3/content', title: 'Bericht', mimeType: 'application/pdf'},
        global: {plugins: [i18n], stubs},
    })
}

describe('FilePreviewModal', () => {
    it('names the file and hands it to the view to draw', () => {
        const preview = open()

        expect(preview.get('[data-testid="file-preview"]').text()).toContain('Bericht')
        expect(preview.findComponent({name: 'FileView'}).props('source')).toBe('/documents/3/content')
    })

    it('leaves saving to whoever opened it', async () => {
        const preview = open()

        await preview.get('[data-testid="file-preview-download"]').trigger('click')

        expect(preview.emitted('download')).toHaveLength(1)
    })
})
