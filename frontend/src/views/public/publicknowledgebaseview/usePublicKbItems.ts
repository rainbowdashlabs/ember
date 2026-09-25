/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type ComputedRef, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {RouteLocationRaw} from 'vue-router'
import * as publicKb from '@/api/publicKb'
import type {PublicSearchResult} from '@/api/publicKb'
import type {KbFile, KbFolder} from '@/api/knowledgeBase'
import {fileIcon} from '@/util/kbFileIcon'
import {
    PICTURED_TYPES,
    useKbFileTypeLabel,
    type KbItem,
} from '@/views/stationview/knowledge/knowledgebaseview/useKbItems'

interface PublicKbSources {
    /** What a picture is asked for by, which is the station's identifier. */
    stationUid: Ref<string>
    /** What a link names the station by, which is the readable name where it has one. */
    stationAddress: Ref<string>
    folders: Ref<KbFolder[]>
    files: Ref<KbFile[]>
}

/**
 * Builds the entries of the public wiki in the shape its own tiles and rows draw, so a reader
 * without an account sees the wiki the station sees.
 *
 * <p>A reader outside the station owns nothing here: there is no entry to rename, move or delete, no
 * mark to set and no favourite to keep, so every entry carries an empty list of actions. The public
 * listing says nothing about how far an entry reaches either, which is a question only somebody
 * inside the station has. What it does carry is the pictures, the icon of a folder and the
 * preview of a file, which are served openly and are fetched as plain images rather than with a
 * session nobody has.
 */
export function usePublicKbItems(sources: PublicKbSources) {
    const {t} = useI18n()
    const fileTypeLabel = useKbFileTypeLabel()

    /** What the folder holds, which is this same page one folder deeper. */
    function folderPage(id: number): RouteLocationRaw {
        return {
            name: 'public-kb',
            params: {stationUid: sources.stationAddress.value},
            query: {folderId: id},
        }
    }

    /** The article the entry names. */
    function filePage(id: number): RouteLocationRaw {
        return {name: 'public-kb-file', params: {stationUid: sources.stationAddress.value, id}}
    }

    function toFolderItem(folder: KbFolder): KbItem {
        return {
            key: 'folder-' + folder.id,
            icon: ['fas', 'folder'],
            iconClass: 'text-[var(--accent)]',
            imageUrl: folder.iconUrl
                ? publicKb.folderIconUrl(sources.stationUid.value, folder.id)
                : undefined,
            publicImages: true,
            title: folder.name,
            description: folder.description || undefined,
            typeLabel: t('kb.typeFolder'),
            updatedAt: folder.updatedAt,
            restricted: folder.restricted === true,
            favourite: false,
            to: folderPage(folder.id),
            actions: [],
        }
    }

    /**
     * A file shows itself where it has a picture, a photograph scaled down or a sheet by its first
     * page, and stands for itself by the icon of its kind everywhere else. The picture comes from
     * the public address, which needs no session and can therefore be drawn by a plain image.
     */
    function toFileItem(file: KbFile): KbItem {
        return {
            key: 'file-' + file.id,
            icon: fileIcon(file),
            iconClass: 'text-[var(--primary)]',
            picture: PICTURED_TYPES.has(file.fileType)
                ? publicKb.filePictureUrl(sources.stationUid.value, file.id)
                : undefined,
            publicImages: true,
            title: file.name,
            description: file.description || undefined,
            typeLabel: fileTypeLabel(file.fileType),
            updatedAt: file.updatedAt,
            restricted: file.restricted === true,
            favourite: false,
            to: filePage(file.id),
            actions: [],
        }
    }

    const items: ComputedRef<KbItem[]> = computed(() => [
        ...sources.folders.value.map(toFolderItem),
        ...sources.files.value.map(toFileItem),
    ])

    /** A hit is the file it found, told apart from the same file in the listing by its key. */
    function toSearchItems(results: PublicSearchResult[]): KbItem[] {
        return results.map(result => ({
            ...toFileItem(result.file),
            key: 'search-' + result.file.id,
            snippet: result.snippet || undefined,
        }))
    }

    return {items, toSearchItems}
}
