/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, provide, ref, type InjectionKey, type Ref} from 'vue'

/** The kind of data a dragged file carries, so a folder takes files of this library and nothing else. */
const DRAGGED_FILE = 'application/x-ember-media-file'

/** What a folder does with a file dropped on it. */
type MoveDropped = (fileId: number, folderId: number | null) => void

const MEDIA_DROP: InjectionKey<MoveDropped> = Symbol('mediaDrop')

/**
 * Lets the files of the media library be dragged onto its folders. The library provides what a drop
 * does; the tiles and the folders, however deep below it they are drawn, only start a drag and take
 * one, so neither needs the other passed down to it.
 *
 * @param move moves the dropped file, and with it the files selected together with it, into a folder,
 *             null for the top level
 */
export function provideMediaDrop(move: MoveDropped): void {
    provide(MEDIA_DROP, move)
}

/** Starts dragging a file of the library, carrying its id. */
export function startFileDrag(event: DragEvent, fileId: number): void {
    if (!event.dataTransfer) return
    event.dataTransfer.setData(DRAGGED_FILE, String(fileId))
    event.dataTransfer.effectAllowed = 'move'
}

function carriesFile(event: DragEvent): boolean {
    return event.dataTransfer?.types.includes(DRAGGED_FILE) ?? false
}

/**
 * Makes an element a place to drop files on: a folder, or the top level. It lights up while a file of
 * the library is held over it and moves the file there when it is let go. Outside the library, where
 * nothing provides a drop, it takes nothing.
 *
 * @param folderId the folder files dropped here go to, null for the top level
 * @return whether a file is held over it, and the listeners its element binds with `v-on`, keyed by
 *     the plain event names that `v-on` takes
 */
export function useFolderDrop(folderId: () => number | null): {
    over: Ref<boolean>
    handlers: {dragover: (event: DragEvent) => void; dragleave: () => void; drop: (event: DragEvent) => void}
} {
    const move = inject(MEDIA_DROP, null)
    const over = ref(false)

    function onDragover(event: DragEvent) {
        if (!move || !carriesFile(event)) return
        event.preventDefault()
        if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
        over.value = true
    }

    function onDragleave() {
        over.value = false
    }

    function onDrop(event: DragEvent) {
        over.value = false
        if (!move || !carriesFile(event)) return
        event.preventDefault()
        const fileId = Number(event.dataTransfer?.getData(DRAGGED_FILE))
        if (Number.isInteger(fileId) && fileId > 0) move(fileId, folderId())
    }

    return {over, handlers: {dragover: onDragover, dragleave: onDragleave, drop: onDrop}}
}
