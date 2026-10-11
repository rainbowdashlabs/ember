/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {defineComponent, h} from 'vue'
import {mount} from '@vue/test-utils'
import {provideMediaDrop, startFileDrag} from '@/composables/useMediaDrop'
import FolderNode from '@/components/media/FolderNode.vue'
import MediaCrumb from './MediaCrumb.vue'
import type {StationFileFolder} from '@/api/generated/schema'

const PHOTOS: StationFileFolder & {children: never[]} = {
    id: 7,
    name: 'Fotos',
    parentId: null,
    sortOrder: 0,
    stationId: '00000000-0000-4000-a000-000000000001',
    createdAt: '2026-10-11T09:00:00Z',
    children: [],
}

/** A data transfer as a browser hands it over while a file is dragged. */
function transfer(): DataTransfer {
    const data = new Map<string, string>()
    return {
        setData: (type: string, value: string) => data.set(type, value),
        getData: (type: string) => data.get(type) ?? '',
        get types() { return [...data.keys()] },
        effectAllowed: 'none',
        dropEffect: 'none',
    } as unknown as DataTransfer
}

function dragEvent(type: string, dataTransfer: DataTransfer): DragEvent {
    const event = new Event(type, {bubbles: true, cancelable: true}) as DragEvent
    Object.defineProperty(event, 'dataTransfer', {value: dataTransfer})
    return event
}

/**
 * A folder of the sidebar tree, as the library draws it: the template binds the drop listeners with
 * `v-on`, which is the binding a drop has to survive.
 */
function sidebarFolder(move: ((fileId: number, folderId: number | null) => void) | null) {
    const wrapper = mount(defineComponent({
        setup() {
            if (move) provideMediaDrop(move)
            return () => h(FolderNode, {folder: PHOTOS, active: null})
        },
    }))
    return {wrapper, folder: () => wrapper.get('[data-testid="media-folder-node"]')}
}

/** The top level as a step of the path above the files, bound the same way. */
function topLevelCrumb(move: (fileId: number, folderId: number | null) => void) {
    return mount(defineComponent({
        setup() {
            provideMediaDrop(move)
            return () => h(MediaCrumb, {folderId: null, current: false}, () => 'Start')
        },
    })).get('[data-testid="media-crumb"]')
}

/** A file of the media library dragged onto a folder moves into it. */
describe('useMediaDrop', () => {
    it('moves a file dropped on a folder of the sidebar into that folder', async () => {
        const move = vi.fn()
        const {wrapper, folder} = sidebarFolder(move)
        const data = transfer()
        startFileDrag(dragEvent('dragstart', data), 42)

        const over = dragEvent('dragover', data)
        folder().element.dispatchEvent(over)
        await wrapper.vm.$nextTick()
        expect(over.defaultPrevented).toBe(true)
        expect(folder().classes()).toContain('!ring-2')

        folder().element.dispatchEvent(dragEvent('drop', data))
        await wrapper.vm.$nextTick()
        expect(move).toHaveBeenCalledWith(42, 7)
        expect(folder().classes()).not.toContain('!ring-2')
    })

    it('moves a file dropped on the top level out of its folder', () => {
        const move = vi.fn()
        const crumb = topLevelCrumb(move)
        const data = transfer()
        startFileDrag(dragEvent('dragstart', data), 42)

        crumb.element.dispatchEvent(dragEvent('drop', data))

        expect(move).toHaveBeenCalledWith(42, null)
    })

    it('takes nothing that is not a file of the library', () => {
        const move = vi.fn()
        const {folder} = sidebarFolder(move)
        const data = transfer()
        data.setData('text/plain', 'irgendwas')

        const over = dragEvent('dragover', data)
        folder().element.dispatchEvent(over)
        folder().element.dispatchEvent(dragEvent('drop', data))

        expect(over.defaultPrevented).toBe(false)
        expect(move).not.toHaveBeenCalled()
    })

    it('takes no drop outside the library', () => {
        const {folder} = sidebarFolder(null)
        const data = transfer()
        startFileDrag(dragEvent('dragstart', data), 42)

        const over = dragEvent('dragover', data)
        folder().element.dispatchEvent(over)

        expect(over.defaultPrevented).toBe(false)
    })
})
