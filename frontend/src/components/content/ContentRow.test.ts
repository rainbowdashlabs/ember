/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readFileSync} from 'node:fs'
import {join} from 'node:path'
import {afterEach, beforeAll, describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import type {VueWrapper} from '@vue/test-utils'
import {CellContentType, type ContentCell, type ContentRow as RowData} from '@/api/generated/schema'
import {publicContentContext} from '@/util/contentContext'
import ContentRow from './ContentRow.vue'
import CellMarkdownInline from './blockeditor/CellMarkdownInline.vue'

/**
 * Text in a cell of a row starts flush with the top of the cell, so beside a picture its first line
 * stands level with the picture's top instead of a paragraph margin lower. Only the edges of the text
 * lose their margin: the spacing between its paragraphs stays, and so does every text outside a row.
 */
describe('text in a row of blocks', () => {
    let mounted: VueWrapper | null = null

    beforeAll(() => {
        const style = document.createElement('style')
        style.textContent = readFileSync(join(import.meta.dirname, '..', '..', 'style.css'), 'utf-8')
        document.head.append(style)
    })

    afterEach(() => {
        mounted?.unmount()
        mounted = null
    })

    function cell(id: number, contentType: ContentCell['contentType'], content: string): ContentCell {
        return {id, rowId: 1, sortOrder: id, widthPercent: 50, contentType, content, config: {}}
    }

    async function renderedRow(markdown: string) {
        const row: RowData = {
            id: 1, containerId: 1, sortOrder: 0, columnLines: false,
            cells: [cell(1, CellContentType.IMAGE, ''), cell(2, CellContentType.MARKDOWN, markdown)],
        }
        mounted = await mountSuspended(ContentRow, {
            attachTo: document.body,
            props: {row, context: publicContentContext('station')},
        })
        return paragraphsOf(mounted)
    }

    async function editorBlock(markdown: string) {
        mounted = await mountSuspended(CellMarkdownInline, {attachTo: document.body, props: {content: markdown}})
        return paragraphsOf(mounted)
    }

    function paragraphsOf(wrapper: VueWrapper) {
        return wrapper.findAll('.markdown-content p').map(paragraph => getComputedStyle(paragraph.element))
    }

    it.each([
        ['on a rendered page', renderedRow],
        ['in the block editor', editorBlock],
    ])('starts and ends flush with the cell %s', async (_where, rendered) => {
        const [first, second, third] = await rendered('eins\n\nzwei\n\ndrei')

        expect(first!.marginTop).toBe('0px')
        expect(third!.marginBottom).toBe('0px')
        expect(first!.marginBottom).toBe(second!.marginTop)
        expect(second!.marginTop).not.toBe('0px')
    })

    it('starts flush with the cell where the first paragraph is aligned', async () => {
        const [first] = await renderedRow('<div data-align="center">\n\neins\n\n</div>\n\nzwei')

        expect(first!.marginTop).toBe('0px')
    })

    it('leaves text outside a row as it is', () => {
        const text = document.createElement('div')
        text.className = 'markdown-content'
        text.innerHTML = '<p>eins</p>'
        document.body.append(text)

        const {marginTop, marginBottom} = getComputedStyle(text.querySelector('p')!)
        text.remove()

        expect([marginTop, marginBottom]).toEqual(['12px', '12px'])
    })
})
