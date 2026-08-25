/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import type {KbFolder, SharedFolderEntry} from '@/api/knowledgeBase'

const {t} = useI18n()

const props = defineProps<{
    currentFolder: KbFolder | null
    breadcrumbs: KbFolder[]
    /**
     * The way back out of a folder belonging to somebody else, outermost first and the one being read
     * last. Empty everywhere but inside a shared folder.
     */
    sharedTrail: SharedFolderEntry[]
    isFavouritesView: boolean
    isKbPublic: boolean
    shareCopied: boolean
    viewMode: 'grid' | 'list'
}>()

const emit = defineEmits<{
    navigate: [folderId: number | null]
    navigateShared: [stationUid: string, folderId: number]
    toggleViewMode: []
    copyShareLink: []
}>()

/**
 * One level out from wherever the reader is: out of the favourites, out of a folder somebody else shared,
 * or out of one of this station's own.
 */
function goUp() {
    if (props.isFavouritesView) {
        emit('navigate', null)
        return
    }
    if (props.sharedTrail.length > 0) {
        const parent = props.sharedTrail[props.sharedTrail.length - 2]
        if (parent?.sourceStationUid) emit('navigateShared', parent.sourceStationUid, parent.id)
        else emit('navigate', null)
        return
    }
    emit('navigate', props.currentFolder?.parentId ?? null)
}
</script>

<template>
    <div class="flex items-center justify-between mb-4">
        <nav class="flex items-center gap-1 text-sm flex-wrap">
            <IconButton
                v-if="currentFolder || isFavouritesView || sharedTrail.length > 0"
                :icon="['fas', 'chevron-up']"
                :label="t('kb.goUp')"
                class="mr-1"
                @click="goUp"
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
            <template v-for="(step, index) in sharedTrail" :key="`shared-${step.id}`">
                <font-awesome-icon :icon="['fas', 'chevron-right']" class="text-xs text-[var(--text-muted)]"/>
                <span
                    class="hover:text-[var(--primary)] transition-colors"
                    :class="index === sharedTrail.length - 1
                        ? 'font-semibold text-[var(--primary)]'
                        : 'cursor-pointer'"
                    @click="step.sourceStationUid && index !== sharedTrail.length - 1
                        && emit('navigateShared', step.sourceStationUid, step.id)"
                >
                    {{ step.name }}
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
