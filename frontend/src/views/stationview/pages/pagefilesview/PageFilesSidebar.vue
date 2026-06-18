/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import IconButton from '@/components/button/IconButton.vue'
import BaseButton from '@/components/button/BaseButton.vue'
import FolderNode from '../pageeditorview/FolderNode.vue'
import type {PageFileFolder, PageFileTag} from '@/api/pageManage'

interface FolderTreeNode extends PageFileFolder {
    children: FolderTreeNode[]
}

defineProps<{
    folderTree: FolderTreeNode[]
    tags: PageFileTag[]
    activeFolder: number | null
    activeTagFilter: number | null
}>()

const emit = defineEmits<{
    'update:activeFolder': [folderId: number | null]
    'update:activeTagFilter': [tagId: number | null]
    'new-folder': []
    'edit-folder': [folder: PageFileFolder]
    'remove-folder': [folder: PageFileFolder]
    'new-tag': []
    'edit-tag': [tag: PageFileTag]
    'remove-tag': [tag: PageFileTag]
}>()

const {t} = useI18n()
</script>

<template>
    <NeutralContainer class="space-y-3">
        <div class="flex items-center justify-between">
            <SectionHeader>{{ t('stationPages.editor.folders') }}</SectionHeader>
            <IconButton :icon="['fas', 'folder-plus']" :label="t('stationPages.editor.newFolder')"
                        @click="emit('new-folder')"/>
        </div>
        <ul class="space-y-1 text-sm">
            <li v-for="root in folderTree" :key="root.id">
                <FolderNode :folder="root" :active="activeFolder"
                            @select="(id: number | null) => emit('update:activeFolder', id)"
                            @edit="(f: PageFileFolder) => emit('edit-folder', f)"
                            @remove="(f: PageFileFolder) => emit('remove-folder', f)"/>
            </li>
        </ul>

        <div class="flex items-center justify-between pt-2">
            <SectionHeader>{{ t('stationPages.editor.tags') }}</SectionHeader>
            <IconButton :icon="['fas', 'plus']" :label="t('stationPages.editor.newTag')" @click="emit('new-tag')"/>
        </div>
        <div class="flex flex-wrap gap-1">
            <BaseButton compact class="!rounded-full !border !font-normal"
                        :class="activeTagFilter === null ? '!border-primary !text-primary' : '!border-(--border) !text-(--text-muted)'"
                        @click="emit('update:activeTagFilter', null)">{{ t('stationPages.editor.allTags') }}</BaseButton>
            <div v-for="tag in tags" :key="tag.id" class="flex items-center">
                <BaseButton compact class="!rounded-l-full !border !font-normal"
                            :style="activeTagFilter === tag.id ? {borderColor: tag.color ?? 'var(--primary)', color: tag.color ?? 'var(--primary)'} : {}"
                            :class="activeTagFilter === tag.id ? '!font-medium' : '!border-(--border)'"
                            @click="emit('update:activeTagFilter', activeTagFilter === tag.id ? null : tag.id)">
                    <span class="inline-block w-2 h-2 rounded-full mr-1" :style="{background: tag.color ?? '#888'}"/>
                    {{ tag.name }}
                </BaseButton>
                <IconButton :icon="['fas', 'pen']" :label="t('common.edit')"
                            class="!p-1 text-xs" @click="emit('edit-tag', tag)"/>
                <IconButton :icon="['fas', 'trash']" :label="t('common.delete')"
                            class="!p-1 text-xs text-error" @click="emit('remove-tag', tag)"/>
            </div>
        </div>
    </NeutralContainer>
</template>
