/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import type {KbFolder, KbFile, SharedFileEntry} from '@/api/knowledgeBase'
import {knowledgeBase} from '@/api'
import AuthImage from '@/components/display/AuthImage.vue'
import {fileIcon} from '@/util/kbFileIcon'

const {t} = useI18n()

defineProps<{
    folders: KbFolder[]
    files: KbFile[]
    sharedFiles: SharedFileEntry[]
    favourites: KbFile[]
    favouriteIds: Set<number>
    canManage: boolean
    isFavouritesView: boolean
    currentFolder: KbFolder | null
}>()

const emit = defineEmits<{
    navigateFolder: [id: number]
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
    <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
        <!-- Virtual Favourites Folder (root only) -->
        <NeutralContainer
            v-if="!currentFolder && !isFavouritesView && favourites.length > 0"
            class="cursor-pointer hover:border-[var(--primary)] transition-colors"
            @click="emit('navigateToFavourites')"
        >
            <div class="flex flex-col items-center gap-2 p-2 text-center">
                <font-awesome-icon :icon="['fas', 'star']" class="text-2xl text-yellow-500"/>
                <span class="text-xs font-medium truncate w-full">{{ t('kb.favourites') }}</span>
                <span class="text-[10px] text-[var(--text-muted)]">{{ favourites.length }} {{ t('kb.files') }}</span>
            </div>
        </NeutralContainer>

        <!-- Folders -->
        <NeutralContainer
            v-for="folder in folders"
            :key="'folder-' + folder.id"
            class="cursor-pointer hover:border-[var(--primary)] transition-colors relative group"
            @click="emit('navigateFolder', folder.id)"
        >
            <div class="flex flex-col items-center gap-2 p-2 text-center">
                <AuthImage
                    v-if="folder.iconUrl"
                    :src="knowledgeBase.folderIconUrl(folder.id)"
                    :alt="folder.name"
                    class="w-8 h-8 rounded object-cover"
                >
                    <template #error>
                        <font-awesome-icon :icon="['fas', 'folder']" class="text-2xl text-[var(--accent)]"/>
                    </template>
                </AuthImage>
                <font-awesome-icon v-else :icon="['fas', 'folder']"
                                   class="text-2xl text-[var(--accent)]"/>
                <div class="flex items-center justify-center gap-1 w-full">
                    <span class="text-sm font-medium truncate">{{ folder.name }}</span>
                    <font-awesome-icon v-if="folder.restricted" :icon="['fas', 'lock']" class="ml-1 h-3 w-3 text-[var(--text-muted)] flex-shrink-0"/>
                </div>
                <span v-if="folder.description"
                      class="text-xs text-[var(--text-muted)] truncate w-full">
                    {{ folder.description }}
                </span>
            </div>
            <div v-if="canManage"
                 class="absolute top-1 right-1 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                <EditButton
                    :label="t('kb.editFolder')"
                    @click.stop="emit('editFolder', folder)"
                />
                <DeleteButton
                    :label="t('kb.deleteFolder')"
                    @click.stop="emit('deleteFolder', folder)"
                />
            </div>
        </NeutralContainer>

        <!-- Files -->
        <NeutralContainer
            v-for="file in files"
            :key="'file-' + file.id"
            class="cursor-pointer hover:border-[var(--primary)] transition-colors relative group"
            @click="emit('navigateFile', file)"
        >
            <div class="flex flex-col items-center gap-2 p-2 text-center">
                <font-awesome-icon :icon="fileIcon(file)" class="text-2xl text-[var(--primary)]"/>
                <div class="flex items-center justify-center gap-1 w-full">
                    <span class="text-sm font-medium truncate">{{ file.name }}</span>
                    <font-awesome-icon v-if="file.restricted" :icon="['fas', 'lock']" class="ml-1 h-3 w-3 text-[var(--text-muted)] flex-shrink-0"/>
                </div>
                <span v-if="file.description"
                      class="text-xs text-[var(--text-muted)] truncate w-full">
                    {{ file.description }}
                </span>
            </div>
            <div class="absolute top-1 left-1 flex gap-1">
                <font-awesome-icon
                    v-if="favouriteIds.has(file.id)"
                    :icon="['fas', 'star']"
                    class="text-xs text-yellow-500"
                />
            </div>
            <div v-if="isFavouritesView"
                 class="absolute top-1 right-1 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                <IconButton
                    :icon="['fas', 'star']"
                    :label="t('kb.removeFavourite')"
                    class="!text-yellow-500"
                    @click.stop="emit('toggleFavourite', file, $event)"
                />
            </div>
            <div v-else-if="canManage"
                 class="absolute top-1 right-1 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                <EditButton
                    :label="t('kb.editFile')"
                    @click.stop="emit('editFile', file)"
                />
                <DeleteButton
                    :label="t('kb.deleteFile')"
                    @click.stop="emit('deleteFile', file)"
                />
            </div>
        </NeutralContainer>

        <!-- Shared Files (from partner stations) -->
        <NeutralContainer
            v-for="shared in sharedFiles"
            :key="'shared-' + shared.file.id"
            class="hover:border-[var(--primary)] transition-colors relative group"
        >
            <div class="flex flex-col items-center gap-2 p-2 text-center">
                <font-awesome-icon :icon="fileIcon(shared.file)" class="text-2xl text-[var(--primary)]"/>
                <span class="text-sm font-medium truncate w-full">{{ shared.file.name }}</span>
                <StationBadge :station-name="shared.stationName" />
                <span v-if="shared.file.description"
                      class="text-xs text-[var(--text-muted)] truncate w-full">
                    {{ shared.file.description }}
                </span>
            </div>
            <div class="absolute top-1 right-1 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                <IconButton
                    :icon="['fas', 'copy']"
                    :label="t('federation.copyToStation')"
                    @click.stop="emit('copySharedFile', shared.file.id)"
                />
            </div>
        </NeutralContainer>
    </div>
</template>
