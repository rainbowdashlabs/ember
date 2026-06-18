/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import BaseButton from '@/components/button/BaseButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import type {PageFileFolder} from '@/api/pageManage'

interface FolderNodeData extends PageFileFolder {
    children: FolderNodeData[]
}

defineProps<{
    folder: FolderNodeData
    active: number | null
}>()

const emit = defineEmits<{
    select: [number | null]
    edit: [PageFileFolder]
    remove: [PageFileFolder]
}>()

const {t} = useI18n()
</script>

<template>
    <div class="space-y-1">
        <div class="flex items-center group">
            <BaseButton compact class="flex-1 !justify-start !font-normal hover:bg-(--bg-accent)"
                        :class="active === folder.id ? '!bg-primary/10 !text-primary' : ''"
                        @click="emit('select', folder.id)">
                <font-awesome-icon :icon="['fas', 'folder']" class="mr-1"/>
                {{ folder.name }}
            </BaseButton>
            <IconButton :icon="['fas', 'pen']" :label="t('common.edit')"
                        class="!p-1 text-xs opacity-0 group-hover:opacity-100"
                        @click="emit('edit', folder)"/>
            <IconButton :icon="['fas', 'trash']" :label="t('common.delete')"
                        class="!p-1 text-xs text-error opacity-0 group-hover:opacity-100"
                        @click="emit('remove', folder)"/>
        </div>
        <div v-if="folder.children.length" class="pl-4 space-y-1">
            <FolderNode v-for="child in folder.children" :key="child.id"
                        :folder="child" :active="active"
                        @select="emit('select', $event)"
                        @edit="emit('edit', $event)"
                        @remove="emit('remove', $event)"/>
        </div>
    </div>
</template>
