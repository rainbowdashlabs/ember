/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type ComputedRef, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {knowledgeBase} from '@/api'
import {
    KbAccessLevel,
    KbFileType,
    levelCovers,
    type KbAccessLevelName,
    type KbFile,
    type KbFolder,
    type SearchResult,
    type SharedFileEntry,
} from '@/api/knowledgeBase'
import {fileIcon} from '@/util/kbFileIcon'
import {isPdfExportable} from '@/util/kbFileExport'

/**
 * One thing a reader can do to an item, rendered as an icon button wherever the item appears.
 */
export interface KbItemAction {
    key: string
    icon: string[]
    label: string
    /** Only shown while the item is hovered. Destructive and administrative actions use this. */
    onHover: boolean
    /** Styling for the standalone button an action gets when it is the only one. */
    class?: string
    /** Styling for the icon of the menu entry an action gets when it shares the item with others. */
    iconClass?: string
    run: (event?: MouseEvent) => void
}

/**
 * A single entry of the knowledge base, whatever it happens to be underneath: the virtual
 * favourites folder, a folder, a file of this station, or a file a partner station shares.
 *
 * The browse grid, the browse list and the search results all render this one shape. They differ
 * in how they lay it out and nothing else - which is what stops a variant from quietly losing the
 * behaviour the others have.
 */
export interface KbItem {
    key: string
    icon: string[]
    iconClass: string
    /** Folder images are served from an authenticated URL; the icon is the fallback. */
    imageUrl?: string
    title: string
    description?: string
    /** The column the list view shows between description and date. */
    typeLabel: string
    updatedAt?: string
    /** Name of the partner station serving the item, for items that are not ours. */
    stationName?: string
    restricted: boolean
    favourite: boolean
    /** Set when a grant holds the reader below what their station permission would allow. */
    levelLabel?: string
    /** A short line the grid shows under the title, used by the favourites folder. */
    countLabel?: string
    /** Rich text the search results show under the title. */
    snippet?: string
    /** Absent when the item cannot be opened, which also removes the pointer cursor. */
    open?: () => void
    actions: KbItemAction[]
}

interface KbItemHandlers {
    openFolder: (id: number) => void
    openFile: (file: KbFile) => void
    openFederatedFile: (stationUid: string, fileId: number) => void
    openFavourites: () => void
    editFolder: (folder: KbFolder) => void
    deleteFolder: (folder: KbFolder) => void
    editFile: (file: KbFile) => void
    deleteFile: (file: KbFile) => void
    exportFilePdf: (file: KbFile) => void
    copySharedFile: (id: number) => void
    removeFavourite: (file: KbFile, event?: MouseEvent) => void
}

interface KbItemSources {
    folders: Ref<KbFolder[]>
    files: Ref<KbFile[]>
    sharedFiles: Ref<SharedFileEntry[]>
    favourites: Ref<KbFile[]>
    favouriteIds: Ref<Set<number>>
    currentFolder: Ref<KbFolder | null>
    isFavouritesView: Ref<boolean>
    canManage: Ref<boolean>
    /** What the reader may do with each folder, as the browse listing reported it. */
    folderLevels: Ref<Record<number, KbAccessLevelName>>
    /** What the reader may do with each file, as the browse listing reported it. */
    fileLevels: Ref<Record<number, KbAccessLevelName>>
}

/**
 * Builds the browse entries and the search entries from the raw lists, attaching to each the
 * actions its kind allows. This is the only place that decides what an entry can do.
 */
export function useKbItems(sources: KbItemSources, handlers: KbItemHandlers) {
    const {t} = useI18n()

    function fileTypeLabel(fileType: string | undefined): string {
        switch (fileType) {
            case KbFileType.MARKDOWN:
                return 'Markdown'
            case KbFileType.PDF:
                return 'PDF'
            case KbFileType.TEXT:
                return t('kb.typeText')
            case KbFileType.IMAGE:
                return t('kb.typeImage')
            case KbFileType.YOUTUBE:
                return 'YouTube'
            case KbFileType.LINK:
                return t('kb.typeLink')
            case KbFileType.PRESENTATION:
                return t('kb.typePresentation')
            default:
                return t('kb.typeFile')
        }
    }

    /**
     * The badge naming the level an entry leaves the reader with. Only someone whose station
     * permission would let them edit gets it: for everyone else read-only is the normal state and
     * saying so on every entry says nothing, while for an editor it is the reason the edit action
     * is missing.
     */
    function levelLabel(level: KbAccessLevelName | undefined): string | undefined {
        if (!sources.canManage.value) return undefined
        if (levelCovers(level, KbAccessLevel.WRITE)) return undefined
        return t('kb.accessLevels.read')
    }

    function pdfAction(file: KbFile, onHover: boolean): KbItemAction[] {
        if (!isPdfExportable(file.fileType)) return []
        return [{
            key: 'pdf',
            icon: ['fas', 'file-pdf'],
            label: t('kb.downloadPdf'),
            onHover,
            run: () => handlers.exportFilePdf(file),
        }]
    }

    function fileActions(file: KbFile): KbItemAction[] {
        const actions: KbItemAction[] = pdfAction(file, true)
        if (sources.isFavouritesView.value) {
            actions.push({
                key: 'unfavourite',
                icon: ['fas', 'star'],
                label: t('kb.removeFavourite'),
                onHover: true,
                class: '!text-yellow-500',
                iconClass: 'text-yellow-500',
                run: (event) => handlers.removeFavourite(file, event),
            })
            return actions
        }
        const level = sources.fileLevels.value[file.id]
        if (sources.canManage.value && levelCovers(level, KbAccessLevel.WRITE)) {
            actions.push({
                key: 'edit',
                icon: ['fas', 'pen'],
                label: t('kb.editFile'),
                onHover: true,
                class: 'text-info-accent hover:bg-info/15 dark:text-info',
                iconClass: 'text-info-accent dark:text-info',
                run: () => handlers.editFile(file),
            })
        }
        if (sources.canManage.value && levelCovers(level, KbAccessLevel.MANAGE)) {
            actions.push({
                key: 'delete',
                icon: ['fas', 'trash'],
                label: t('kb.deleteFile'),
                onHover: true,
                class: 'text-error hover:bg-error/15',
                iconClass: 'text-error',
                run: () => handlers.deleteFile(file),
            })
        }
        return actions
    }

    function toFileItem(file: KbFile): KbItem {
        return {
            key: 'file-' + file.id,
            icon: fileIcon(file),
            iconClass: 'text-[var(--primary)]',
            title: file.name,
            description: file.description || undefined,
            typeLabel: fileTypeLabel(file.fileType),
            updatedAt: file.updatedAt,
            restricted: file.restricted === true,
            favourite: sources.favouriteIds.value.has(file.id),
            levelLabel: sources.isFavouritesView.value
                ? undefined
                : levelLabel(sources.fileLevels.value[file.id]),
            open: () => handlers.openFile(file),
            actions: fileActions(file),
        }
    }

    function toSharedItem(shared: SharedFileEntry): KbItem {
        const stationUid = shared.sourceStationUid
        return {
            key: 'shared-' + stationUid + '-' + shared.file.id,
            icon: fileIcon(shared.file),
            iconClass: 'text-[var(--primary)]',
            title: shared.file.name,
            description: shared.file.description || undefined,
            typeLabel: fileTypeLabel(shared.file.fileType),
            stationName: shared.stationName,
            restricted: false,
            favourite: false,
            open: stationUid ? () => handlers.openFederatedFile(stationUid, shared.file.id) : undefined,
            actions: [
                {
                    key: 'copy',
                    icon: ['fas', 'copy'],
                    label: t('federation.copyToStation'),
                    onHover: true,
                    run: () => handlers.copySharedFile(shared.file.id),
                },
            ],
        }
    }

    /**
     * Editing needs write, deleting needs manage - the same levels the server enforces, so the
     * listing offers exactly the actions that will be accepted.
     */
    function folderActions(folder: KbFolder): KbItemAction[] {
        if (!sources.canManage.value) return []
        const level = sources.folderLevels.value[folder.id]
        const actions: KbItemAction[] = []
        if (levelCovers(level, KbAccessLevel.WRITE)) {
            actions.push({
                key: 'edit',
                icon: ['fas', 'pen'],
                label: t('kb.editFolder'),
                onHover: true,
                class: 'text-info-accent hover:bg-info/15 dark:text-info',
                iconClass: 'text-info-accent dark:text-info',
                run: () => handlers.editFolder(folder),
            })
        }
        if (levelCovers(level, KbAccessLevel.MANAGE)) {
            actions.push({
                key: 'delete',
                icon: ['fas', 'trash'],
                label: t('kb.deleteFolder'),
                onHover: true,
                class: 'text-error hover:bg-error/15',
                iconClass: 'text-error',
                run: () => handlers.deleteFolder(folder),
            })
        }
        return actions
    }

    function toFolderItem(folder: KbFolder): KbItem {
        return {
            key: 'folder-' + folder.id,
            icon: ['fas', 'folder'],
            iconClass: 'text-[var(--accent)]',
            imageUrl: folder.iconUrl ? knowledgeBase.folderIconUrl(folder.id) : undefined,
            title: folder.name,
            description: folder.description || undefined,
            typeLabel: t('kb.typeFolder'),
            updatedAt: folder.updatedAt,
            restricted: folder.restricted === true,
            favourite: false,
            levelLabel: levelLabel(sources.folderLevels.value[folder.id]),
            open: () => handlers.openFolder(folder.id),
            actions: folderActions(folder),
        }
    }

    const favouritesItem = computed<KbItem | null>(() => {
        const showFavouritesFolder = !sources.currentFolder.value
            && !sources.isFavouritesView.value
            && sources.favourites.value.length > 0
        if (!showFavouritesFolder) return null
        return {
            key: 'favourites',
            icon: ['fas', 'star'],
            iconClass: 'text-yellow-500',
            title: t('kb.favourites'),
            typeLabel: t('kb.typeFolder'),
            restricted: false,
            favourite: false,
            countLabel: `${sources.favourites.value.length} ${t('kb.files')}`,
            open: () => handlers.openFavourites(),
            actions: [],
        }
    })

    const items: ComputedRef<KbItem[]> = computed(() => {
        const result: KbItem[] = []
        if (favouritesItem.value) result.push(favouritesItem.value)
        result.push(...sources.folders.value.map(toFolderItem))
        result.push(...sources.files.value.map(toFileItem))
        result.push(...sources.sharedFiles.value.map(toSharedItem))
        return result
    })

    /**
     * Maps search hits onto the same shape. A hit from a partner station carries its snippet and
     * a copy action; a local hit offers the export and nothing else, because a hit is reached
     * without walking the folders whose levels decide what may be done to it.
     */
    function toSearchItems(results: SearchResult[]): KbItem[] {
        return results.map((result) => {
            const stationUid = result.sourceStationUid
            if (!stationUid) {
                return {
                    ...toFileItem(result.file),
                    key: 'search-local-' + result.file.id,
                    snippet: result.snippet || undefined,
                    actions: pdfAction(result.file, false),
                }
            }
            return {
                key: 'search-' + stationUid + '-' + result.file.id,
                icon: fileIcon(result.file),
                iconClass: 'text-[var(--primary)]',
                title: result.file.name,
                description: result.file.description || undefined,
                typeLabel: fileTypeLabel(result.file.fileType),
                snippet: result.snippet || undefined,
                stationName: result.stationName ?? undefined,
                restricted: false,
                favourite: false,
                open: () => handlers.openFederatedFile(stationUid, result.file.id),
                actions: [
                    {
                        key: 'copy',
                        icon: ['fas', 'copy'],
                        label: t('federation.copyToStation'),
                        onHover: false,
                        run: () => handlers.copySharedFile(result.file.id),
                    },
                ],
            }
        })
    }

    return {items, toSearchItems}
}
