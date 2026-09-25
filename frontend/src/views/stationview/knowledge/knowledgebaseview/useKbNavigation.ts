/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {useRoute, useRouter, type RouteLocationRaw} from 'vue-router'
import type {KbFileSummary} from '@/api/knowledgeBase'

/** Where the knowledge base is mounted: a station's own screens, or an association's. */
export type KbRoutes = {browse: string; file: string; versions: string}

export const STATION_KB_ROUTES: KbRoutes = {browse: 'kb-browse', file: 'kb-file', versions: 'kb-versions'}

/**
 * Route-derived browse position of the knowledge base plus the navigation
 * helpers that move between folders, files and the favourites list.
 *
 * <p>An association's knowledge base is the same screens over its own station, reached at its own addresses,
 * so which pages a click lands on is the one thing that differs between the two.
 *
 * @param routes the pages this knowledge base is mounted on
 */
export function useKbNavigation(routes: KbRoutes = STATION_KB_ROUTES) {
    const router = useRouter()
    const route = useRoute()

    const folderParam = computed(() => route.query.folderId)

    const isFavouritesView = computed(() => route.query.folderId === 'favourites')

    /**
     * Whether the trash is open. It is a state of the same screen rather than a page of its own, the
     * way the favourites are, so an association reaches it at its own address without a second set
     * of pages existing for it.
     */
    const isTrashView = computed(() => route.query.folderId === 'trash')

    /**
     * The folder of a partner being read, if one is open.
     *
     * <p>A folder id belongs to the station that owns it, so a partner's folder is addressed by the pair.
     * Both sit in the query rather than the path: it is the same wiki screen either way, showing somebody
     * else's level of it.
     */
    const sharedStationUid = computed(() => (route.query.sharedStation as string) || null)

    const sharedFolderId = computed(() => {
        const param = route.query.sharedFolder
        return param ? Number(param) : null
    })

    const isSharedFolderView = computed(() => sharedStationUid.value !== null && sharedFolderId.value !== null)

    /**
     * A folder of a partner, addressed by the pair, because a folder id belongs to the station that
     * owns it.
     */
    function sharedFolderPage(stationUid: string, folderId: number): RouteLocationRaw {
        return {name: routes.browse, query: {sharedStation: stationUid, sharedFolder: folderId}}
    }

    function navigateToSharedFolder(stationUid: string, folderId: number) {
        router.push(sharedFolderPage(stationUid, folderId))
    }

    const currentFolderId = computed(() => {
        const param = route.query.folderId
        if (!param || param === 'favourites' || param === 'trash') return null
        return Number(param)
    })

    function folderPage(folderId: number | null): RouteLocationRaw {
        return folderId === null ? {name: routes.browse} : {name: routes.browse, query: {folderId}}
    }

    function navigateToFolder(folderId: number | null) {
        router.push(folderPage(folderId))
    }

    function filePage(file: Pick<KbFileSummary, 'id'>): RouteLocationRaw {
        return {name: routes.file, params: {id: file.id}}
    }

    /**
     * A file held by a federation partner. A file id is only unique within the station that owns it,
     * so the partner's station UUID is part of the address.
     */
    function federatedFilePage(stationUid: string, fileId: number): RouteLocationRaw {
        return {name: 'federated-kb-file', params: {stationUid, fileId}}
    }

    function favouritesPage(): RouteLocationRaw {
        return {name: routes.browse, query: {folderId: 'favourites'}}
    }

    function navigateToTrash() {
        router.push({name: routes.browse, query: {folderId: 'trash'}})
    }

    return {
        folderParam,
        isFavouritesView,
        isTrashView,
        currentFolderId,
        sharedStationUid,
        sharedFolderId,
        isSharedFolderView,
        folderPage,
        filePage,
        federatedFilePage,
        favouritesPage,
        sharedFolderPage,
        navigateToSharedFolder,
        navigateToFolder,
        navigateToTrash,
    }
}
