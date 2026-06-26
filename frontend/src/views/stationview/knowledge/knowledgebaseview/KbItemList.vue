/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import type {KbFolder, KbFile, SharedFileEntry} from '@/api/knowledgeBase'
import {KbFileType} from '@/api/knowledgeBase'
import {knowledgeBase} from '@/api'
import AuthImage from '@/components/display/AuthImage.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import {fileIcon} from '@/util/kbFileIcon'
import {formatDate} from '@/util/format'

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
    navigateToFavourites: []
}>()

function fileTypeLabel(file: KbFile): string {
    switch (file.fileType) {
        case KbFileType.MARKDOWN:
            return 'Markdown'
        case KbFileType.PDF:
            return 'PDF'
        case KbFileType.TEXT:
            return 'Text'
        case KbFileType.IMAGE:
            return 'Bild'
        case KbFileType.YOUTUBE:
            return 'YouTube'
        case KbFileType.LINK:
            return 'Link'
        default:
            return 'Datei'
    }
}

</script>

<template>
    <div class="border border-[var(--border)] rounded-lg overflow-hidden divide-y divide-[var(--border)]">
        <!-- Virtual Favourites Folder (root only, list view) -->
        <div
            v-if="!currentFolder && !isFavouritesView && favourites.length > 0"
            class="flex items-center gap-2 px-3 py-1.5 cursor-pointer hover:bg-[var(--bg-accent)] transition-colors"
            @click="emit('navigateToFavourites')"
        >
            <div class="w-5 flex-shrink-0 flex justify-center">
                <font-awesome-icon :icon="['fas', 'star']" class="text-sm text-yellow-500"/>
            </div>
            <span class="text-sm font-medium truncate min-w-0 flex-1">{{ t('kb.favourites') }}</span>
            <span class="text-xs text-[var(--text-muted)]">{{ favourites.length }}</span>
        </div>

        <!-- Folders -->
        <div
            v-for="folder in folders"
            :key="'folder-' + folder.id"
            class="flex items-center gap-2 px-3 py-1.5 cursor-pointer hover:bg-[var(--bg-accent)] transition-colors group"
            @click="emit('navigateFolder', folder.id)"
        >
            <div class="w-5 flex-shrink-0 flex justify-center">
                <AuthImage
                    v-if="folder.iconUrl"
                    :src="knowledgeBase.folderIconUrl(folder.id)"
                    :alt="folder.name"
                    class="w-4 h-4 rounded object-cover"
                >
                    <template #error>
                        <font-awesome-icon :icon="['fas', 'folder']" class="text-sm text-[var(--accent)]"/>
                    </template>
                </AuthImage>
                <font-awesome-icon v-else :icon="['fas', 'folder']"
                                   class="text-sm text-[var(--accent)]"/>
            </div>
            <span class="text-sm font-medium truncate min-w-0 flex-1">{{ folder.name }}</span>
            <MutedIcon v-if="folder.restricted" :icon="['fas', 'lock']" class="flex-shrink-0 ml-1"/>
            <span v-if="folder.description"
                  class="hidden sm:block text-xs text-[var(--text-muted)] truncate max-w-48">
                {{ folder.description }}
            </span>
            <span class="hidden md:block text-xs text-[var(--text-muted)] w-16 text-right flex-shrink-0">Ordner</span>
            <span class="hidden md:block text-xs text-[var(--text-muted)] w-24 text-right flex-shrink-0">
                {{ formatDate(folder.updatedAt) }}
            </span>
            <div v-if="canManage"
                 class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0">
                <EditButton
                    :label="t('kb.editFolder')"
                    @click.stop="emit('editFolder', folder)"
                />
                <DeleteButton
                    :label="t('kb.deleteFolder')"
                    @click.stop="emit('deleteFolder', folder)"
                />
            </div>
        </div>

        <!-- Files -->
        <div
            v-for="file in files"
            :key="'file-' + file.id"
            class="flex items-center gap-2 px-3 py-1.5 cursor-pointer hover:bg-[var(--bg-accent)] transition-colors group"
            @click="emit('navigateFile', file)"
        >
            <div class="w-5 flex-shrink-0 flex justify-center">
                <font-awesome-icon :icon="fileIcon(file)" class="text-sm text-[var(--primary)]"/>
            </div>
            <font-awesome-icon
                v-if="favouriteIds.has(file.id)"
                :icon="['fas', 'star']"
                class="text-xs text-yellow-500 flex-shrink-0"
            />
            <span class="text-sm font-medium truncate min-w-0 flex-1">{{ file.name }}</span>
            <MutedIcon v-if="file.restricted" :icon="['fas', 'lock']" class="flex-shrink-0 ml-1"/>
            <span v-if="file.description"
                  class="hidden sm:block text-xs text-[var(--text-muted)] truncate max-w-48">
                {{ file.description }}
            </span>
            <span class="hidden md:block text-xs text-[var(--text-muted)] w-16 text-right flex-shrink-0">
                {{ fileTypeLabel(file) }}
            </span>
            <span class="hidden md:block text-xs text-[var(--text-muted)] w-24 text-right flex-shrink-0">
                {{ formatDate(file.updatedAt) }}
            </span>
            <div v-if="canManage"
                 class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0">
                <EditButton
                    :label="t('kb.editFile')"
                    @click.stop="emit('editFile', file)"
                />
                <DeleteButton
                    :label="t('kb.deleteFile')"
                    @click.stop="emit('deleteFile', file)"
                />
            </div>
        </div>

        <!-- Shared Files (from partner stations) -->
        <div
            v-for="shared in sharedFiles"
            :key="'shared-' + shared.file.id"
            class="flex items-center gap-2 px-3 py-1.5 hover:bg-[var(--bg-accent)] transition-colors group"
        >
            <div class="w-5 flex-shrink-0 flex justify-center">
                <font-awesome-icon :icon="fileIcon(shared.file)" class="text-sm text-[var(--primary)]"/>
            </div>
            <span class="text-sm font-medium truncate min-w-0 flex-1">{{ shared.file.name }}</span>
            <StationBadge :station-name="shared.stationName" />
            <span v-if="shared.file.description"
                  class="hidden sm:block text-xs text-[var(--text-muted)] truncate max-w-48">
                {{ shared.file.description }}
            </span>
            <span class="hidden md:block text-xs text-[var(--text-muted)] w-16 text-right flex-shrink-0">
                {{ fileTypeLabel(shared.file) }}
            </span>
            <div class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0">
                <IconButton
                    :icon="['fas', 'copy']"
                    :label="t('federation.copyToStation')"
                    @click.stop="emit('copySharedFile', shared.file.id)"
                />
            </div>
        </div>
    </div>
</template>
