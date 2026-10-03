/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {defineComponent, h, ref, type ComputedRef} from 'vue'
import {CellContentType} from '@/api/generated/schema'
import {provideBlockEditorOptions, useBlockEditorOptions, type BlockEditorOptions} from './useBlockEditorOptions'

/**
 * The block editor's options for surfaces other than a page. Nothing provided is a page's editor,
 * which is what keeps pages, news, the wiki and system news as they were.
 *
 * @vitest-environment happy-dom
 */
describe('useBlockEditorOptions', () => {
    function read(provide?: () => void): ComputedRef<BlockEditorOptions> {
        let options: ComputedRef<BlockEditorOptions> | null = null
        const Child = defineComponent({
            setup() {
                options = useBlockEditorOptions()
                return () => h('div')
            },
        })
        mount(defineComponent({
            setup() {
                provide?.()
                return () => h(Child)
            },
        }))
        if (!options) throw new Error('the child did not set up')
        return options
    }

    it('are a page editor where nothing is provided', () => {
        const options = read()

        expect(options.value).toEqual({maxColumns: 4})
    })

    it('take what a surface provides, on top of the defaults', () => {
        const options = read(() => provideBlockEditorOptions({
            allowedKinds: [CellContentType.MARKDOWN],
            stationLogo: true,
        }))

        expect(options.value.maxColumns).toBe(4)
        expect(options.value.allowedKinds).toEqual([CellContentType.MARKDOWN])
        expect(options.value.stationLogo).toBe(true)
    })

    it('follow a provided getter, so choices loaded later reach the blocks', () => {
        const columns = ref(3)
        const options = read(() => provideBlockEditorOptions(() => ({maxColumns: columns.value})))

        expect(options.value.maxColumns).toBe(3)
        columns.value = 2
        expect(options.value.maxColumns).toBe(2)
    })
})
