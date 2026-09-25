/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it, vi} from 'vitest'
import {computed, ref} from 'vue'
import {
    KbFavouriteTarget,
    type FavouriteEntry,
    type KbFavourite,
    type KbFileSummary,
    type SharedFileEntry,
} from '@/api/knowledgeBase'
import {favouriteKey} from '@/composables/useKbFavourites'
import {useKbItems} from './useKbItems'

vi.mock('vue-i18n', async (original) => ({
    ...(await original<typeof import('vue-i18n')>()),
    useI18n: () => ({t: (key: string) => key}),
}))
vi.mock('@/api', () => ({
    knowledgeBase: {
        filePictureUrl: (id: number) => `/kb/files/${id}/picture`,
        folderIconUrl: (id: number) => `/kb/folders/${id}/icon`,
    },
}))

const PARTNER = '00000000-0000-4000-a000-0000000000f1'

const file: KbFileSummary = {
    id: 7,
    stationId: 'station',
    folderId: null,
    name: 'Hydrantenplan',
    description: '',
    fileType: 'PDF',
    updatedAt: '2026-09-21T10:00:00Z',
    restricted: false,
}

const partnerFile: SharedFileEntry = {
    file: {id: 4, name: 'Atemschutz', description: '', fileType: 'PDF'},
    stationName: 'Nachbarwache',
    sourceStationUid: PARTNER,
}

function favourite(overrides: Partial<KbFavourite>): KbFavourite {
    return {
        id: 1,
        target: KbFavouriteTarget.FILE,
        entryId: 7,
        partnerStationUid: null,
        title: 'Hydrantenplan',
        fileType: 'PDF',
        stationName: null,
        createdAt: '2026-09-21T10:00:00Z',
        ...overrides,
    }
}

function build(options: {favourites?: KbFavourite[]; favouritesView?: boolean} = {}) {
    const list = ref(options.favourites ?? [])
    const keys = computed(() => new Set(list.value.map(favouriteKey)))
    const handlers = {
        folderPage: (id: number) => ({name: 'kb-browse', query: {folderId: id}}),
        filePage: (target: {id: number}) => ({name: 'kb-file', params: {id: target.id}}),
        federatedFilePage: (stationUid: string, fileId: number) =>
            ({name: 'federated-kb-file', params: {stationUid, fileId}}),
        favouritesPage: () => ({name: 'kb-browse', query: {folderId: 'favourites'}}),
        editFolder: vi.fn(),
        shareFolder: vi.fn(),
        moveFolder: vi.fn(),
        deleteFolder: vi.fn(),
        editFile: vi.fn(),
        shareFile: vi.fn(),
        moveFile: vi.fn(),
        deleteFile: vi.fn(),
        exportFilePdf: vi.fn(),
        downloadFile: vi.fn(),
        copySharedFile: vi.fn(),
        sharedFolderPage: (stationUid: string, folderId: number) =>
            ({name: 'kb-browse', query: {sharedStation: stationUid, sharedFolder: folderId}}),
        toggleFavourite: vi.fn(),
    }
    const {items} = useKbItems({
        folders: ref([]),
        files: ref([file]),
        sharedFiles: ref([partnerFile]),
        sharedFolders: ref([]),
        publicIds: ref(new Set<number>()),
        federatedIds: ref(new Set<number>()),
        narrowIds: ref(new Set<number>()),
        folderKey: (id: number) => id,
        fileKey: (id: number) => -id,
        favourites: {
            favourites: computed(() => list.value),
            isFavourite: (entry: FavouriteEntry) => keys.value.has(favouriteKey(entry)),
        },
        currentFolder: ref(null),
        isFavouritesView: ref(options.favouritesView ?? false),
        canManage: ref(false),
        folderLevels: ref({}),
        fileLevels: ref({}),
    }, handlers)
    return {items, handlers}
}

function favouriteActionOf(items: {value: {key: string; actions: {key: string; label: string}[]}[]}, key: string) {
    return items.value.find(item => item.key === key)?.actions.find(action => action.key === 'favourite')
}

/**
 * Where a reader marks something and where they find it again: the star among every tile's
 * actions, the favourites folder at the root, and the view behind it.
 */
describe('useKbItems favourites', () => {
    it('offers to mark a file that is not a favourite', () => {
        const {items} = build()

        expect(favouriteActionOf(items, 'file-7')?.label).toBe('kb.addFavourite')
    })

    it('offers to take the mark off a file that is one, and shows its star', () => {
        const {items} = build({favourites: [favourite({})]})

        expect(favouriteActionOf(items, 'file-7')?.label).toBe('kb.removeFavourite')
        expect(items.value.find(item => item.key === 'file-7')?.favourite).toBe(true)
    })

    it('lets a partner file be marked, naming the partner with it', () => {
        const {items, handlers} = build()
        const shared = items.value.find(item => item.key === `shared-${PARTNER}-4`)

        shared?.actions.find(action => action.key === 'favourite')?.run()

        expect(handlers.toggleFavourite).toHaveBeenCalledWith(
            {target: KbFavouriteTarget.PARTNER_FILE, entryId: 4, partnerStationUid: PARTNER},
            undefined,
        )
    })

    it('shows the favourites folder at the root once there is something in it', () => {
        expect(build().items.value.some(item => item.key === 'favourites')).toBe(false)
        expect(build({favourites: [favourite({})]}).items.value.some(item => item.key === 'favourites')).toBe(true)
    })

    it('lists folders before files in the favourites view, and addresses each where it lives', () => {
        const {items} = build({
            favouritesView: true,
            favourites: [
                favourite({id: 1, target: KbFavouriteTarget.PARTNER_FILE, entryId: 4, partnerStationUid: PARTNER}),
                favourite({id: 2, target: KbFavouriteTarget.FOLDER, entryId: 3, title: 'Einsatz', fileType: null}),
            ],
        })

        expect(items.value.map(item => item.title)).toEqual(['Einsatz', 'Hydrantenplan'])
        expect(items.value[1]?.to).toEqual({name: 'federated-kb-file', params: {stationUid: PARTNER, fileId: 4}})
    })
})
