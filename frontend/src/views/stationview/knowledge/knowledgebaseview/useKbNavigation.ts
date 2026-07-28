/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import type {KbFile} from '@/api/knowledgeBase'

/**
 * Route-derived browse position of the knowledge base plus the navigation
 * helpers that move between folders, files and the favourites list.
 */
export function useKbNavigation() {
    const router = useRouter()
    const route = useRoute()

    const folderParam = computed(() => route.query.folderId)

    const isFavouritesView = computed(() => route.query.folderId === 'favourites')

    const currentFolderId = computed(() => {
        const param = route.query.folderId
        if (!param || param === 'favourites') return null
        return Number(param)
    })

    function navigateToFolder(folderId: number | null) {
        if (folderId === null) {
            router.push({name: 'kb-browse'})
        } else {
            router.push({name: 'kb-browse', query: {folderId}})
        }
    }

    function navigateToFile(file: KbFile) {
        router.push({name: 'kb-file', params: {id: file.id}})
    }

    function navigateToFavourites() {
        router.push({name: 'kb-browse', query: {folderId: 'favourites'}})
    }

    return {
        folderParam,
        isFavouritesView,
        currentFolderId,
        navigateToFolder,
        navigateToFile,
        navigateToFavourites,
    }
}
