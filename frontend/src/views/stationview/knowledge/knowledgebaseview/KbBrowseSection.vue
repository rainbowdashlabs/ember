/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import MutedText from '@/components/typography/MutedText.vue'
import KbCreateMenu from './KbCreateMenu.vue'
import KbItemGrid from './KbItemGrid.vue'
import KbItemList from './KbItemList.vue'
import type {KbFolder, KbFile, SharedFileEntry} from '@/api/knowledgeBase'

const {t} = useI18n()

defineProps<{
    loading: boolean
    currentFolder: KbFolder | null
    isFavouritesView: boolean
    favourites: KbFile[]
    viewMode: 'grid' | 'list'
    filteredFolders: KbFolder[]
    filteredFiles: KbFile[]
    filteredSharedFiles: SharedFileEntry[]
    favouriteIds: Set<number>
    canManage: boolean
}>()

const emit = defineEmits<{
    createFolder: []
    createMarkdown: []
    upload: []
    youtube: []
    link: []
    importDocument: []
    navigateFolder: [folderId: number | null]
    navigateFile: [file: KbFile]
    editFolder: [folder: KbFolder]
    deleteFolder: [folder: KbFolder]
    editFile: [file: KbFile]
    deleteFile: [file: KbFile]
    copySharedFile: [id: number]
    toggleFavourite: [file: KbFile, event?: MouseEvent]
    navigateToFavourites: []
}>()
</script>

<template>
    <div>
        <Spinner v-if="loading"/>
        <template v-else>
            <MutedText tag="p" size="sm" v-if="currentFolder?.description">
                {{ currentFolder.description }}
            </MutedText>

            <KbCreateMenu
                v-if="canManage"
                @create-folder="emit('createFolder')"
                @create-markdown="emit('createMarkdown')"
                @upload="emit('upload')"
                @youtube="emit('youtube')"
                @link="emit('link')"
                @import-document="emit('importDocument')"
            />

            <template v-if="filteredFolders.length > 0 || filteredFiles.length > 0 || filteredSharedFiles.length > 0 || (!currentFolder && !isFavouritesView && favourites.length > 0)">
                <KbItemGrid
                    v-if="viewMode === 'grid'"
                    :folders="filteredFolders"
                    :files="filteredFiles"
                    :shared-files="filteredSharedFiles"
                    :favourites="favourites"
                    :favourite-ids="favouriteIds"
                    :can-manage="canManage"
                    :is-favourites-view="isFavouritesView"
                    :current-folder="currentFolder"
                    @navigate-folder="(id) => emit('navigateFolder', id)"
                    @navigate-file="(file) => emit('navigateFile', file)"
                    @edit-folder="(folder) => emit('editFolder', folder)"
                    @delete-folder="(folder) => emit('deleteFolder', folder)"
                    @edit-file="(file) => emit('editFile', file)"
                    @delete-file="(file) => emit('deleteFile', file)"
                    @copy-shared-file="(id) => emit('copySharedFile', id)"
                    @toggle-favourite="(file, event) => emit('toggleFavourite', file, event)"
                    @navigate-to-favourites="emit('navigateToFavourites')"
                />
                <KbItemList
                    v-else
                    :folders="filteredFolders"
                    :files="filteredFiles"
                    :shared-files="filteredSharedFiles"
                    :favourites="favourites"
                    :favourite-ids="favouriteIds"
                    :can-manage="canManage"
                    :is-favourites-view="isFavouritesView"
                    :current-folder="currentFolder"
                    @navigate-folder="(id) => emit('navigateFolder', id)"
                    @navigate-file="(file) => emit('navigateFile', file)"
                    @edit-folder="(folder) => emit('editFolder', folder)"
                    @delete-folder="(folder) => emit('deleteFolder', folder)"
                    @edit-file="(file) => emit('editFile', file)"
                    @delete-file="(file) => emit('deleteFile', file)"
                    @copy-shared-file="(id) => emit('copySharedFile', id)"
                    @navigate-to-favourites="emit('navigateToFavourites')"
                />
            </template>

            <p v-else class="text-[var(--text-muted)] text-center py-8">
                {{ t('kb.emptyFolder') }}
            </p>
        </template>
    </div>
</template>
