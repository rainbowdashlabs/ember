/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import type {KbFolder} from '@/api/knowledgeBase'

const {t} = useI18n()

defineProps<{
    currentFolder: KbFolder | null
    breadcrumbs: KbFolder[]
    isFavouritesView: boolean
    isKbPublic: boolean
    shareCopied: boolean
    viewMode: 'grid' | 'list'
}>()

const emit = defineEmits<{
    navigate: [folderId: number | null]
    toggleViewMode: []
    copyShareLink: []
}>()
</script>

<template>
    <div class="flex items-center justify-between mb-4">
        <nav class="flex items-center gap-1 text-sm flex-wrap">
            <IconButton
                v-if="currentFolder || isFavouritesView"
                :icon="['fas', 'chevron-up']"
                :label="t('kb.goUp')"
                class="mr-1"
                @click="isFavouritesView ? emit('navigate', null) : emit('navigate', currentFolder?.parentId ?? null)"
            />
            <span
                class="hover:text-[var(--primary)] transition-colors cursor-pointer"
                :class="{'font-semibold text-[var(--primary)]': !currentFolder && !isFavouritesView}"
                @click="emit('navigate', null)"
            >
                {{ t('kb.root') }}
            </span>
            <template v-if="isFavouritesView">
                <font-awesome-icon :icon="['fas', 'chevron-right']" class="text-xs text-[var(--text-muted)]"/>
                <span class="font-semibold text-[var(--primary)]">
                    <font-awesome-icon :icon="['fas', 'star']" class="text-yellow-500 mr-1"/>
                    {{ t('kb.favourites') }}
                </span>
            </template>
            <template v-for="crumb in breadcrumbs" :key="crumb.id">
                <font-awesome-icon :icon="['fas', 'chevron-right']" class="text-xs text-[var(--text-muted)]"/>
                <span
                    class="hover:text-[var(--primary)] transition-colors cursor-pointer"
                    :class="{'font-semibold text-[var(--primary)]': crumb.id === currentFolder?.id}"
                    @click="emit('navigate', crumb.id)"
                >
                    {{ crumb.name }}
                </span>
            </template>
        </nav>
        <div class="flex items-center gap-1">
            <IconButton
                v-if="isKbPublic"
                :icon="['fas', shareCopied ? 'check' : 'share-nodes']"
                :label="t('kb.shareLink')"
                :class="shareCopied ? '!text-green-500' : ''"
                @click="emit('copyShareLink')"
            />
            <IconButton
                :icon="['fas', viewMode === 'grid' ? 'table-columns' : 'grip-vertical']"
                :label="viewMode === 'grid' ? t('kb.listView') : t('kb.gridView')"
                @click="emit('toggleViewMode')"
            />
        </div>
    </div>
</template>
