/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {knowledgeBase} from '@/api'
import type {KbFile, KbFolder, KbFolderTreeEntry} from '@/api/knowledgeBase'

/**
 * The folder tree a move picks from, and which entry is being moved.
 *
 * Both the browse screen and the article page offer a move, and both need the same two things: the
 * whole tree with what the reader may do in each folder, and one entry at a time to move. Reading
 * the tree fails quietly to an empty one, because a picker with nothing in it still offers the tree
 * root, which is a real place to put something.
 */
export function useKbMoveTarget() {
    const folders = ref<KbFolderTreeEntry[]>([])
    const showMove = ref(false)
    const movingFolder = ref<KbFolder | null>(null)
    const movingFile = ref<KbFile | null>(null)

    async function reloadFolders() {
        try {
            folders.value = await knowledgeBase.listFolderTree()
        } catch {
            folders.value = []
        }
    }

    function moveFolder(folder: KbFolder) {
        movingFolder.value = folder
        movingFile.value = null
        showMove.value = true
    }

    function moveFile(file: KbFile) {
        movingFolder.value = null
        movingFile.value = file
        showMove.value = true
    }

    return {folders, showMove, movingFolder, movingFile, reloadFolders, moveFolder, moveFile}
}
