/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import MutedText from '@/components/typography/MutedText.vue'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import KbCreateMenu from './KbCreateMenu.vue'
import KbItemGrid from './KbItemGrid.vue'
import KbItemList from './KbItemList.vue'
import KbSelectionBar from './KbSelectionBar.vue'
import type {KbFolder} from '@/api/knowledgeBase'
import type {KbItem} from './useKbItems'

const {t} = useI18n()

withDefaults(defineProps<{
    loading: boolean
    currentFolder: KbFolder | null
    viewMode: 'grid' | 'list'
    items: KbItem[]
    canManage: boolean
    /** Whether entries carry a box to mark them with. */
    selecting?: boolean
    selectedKeys?: string[]
    selectedCount?: number
}>(), {selecting: false, selectedKeys: () => [], selectedCount: 0})

const emit = defineEmits<{
    createFolder: []
    createMarkdown: []
    upload: []
    youtube: []
    link: []
    importDocument: []
    toggleSelecting: []
    toggleSelect: [key: string, value: boolean, shift: boolean]
    moveSelection: []
    tagSelection: []
    clearSelection: []
}>()
</script>

<template>
    <div>
        <Spinner v-if="loading"/>
        <template v-else>
            <MutedText tag="p" size="sm" v-if="currentFolder?.description">
                {{ currentFolder.description }}
            </MutedText>

            <div v-if="canManage" class="flex flex-wrap items-center gap-2 mb-4">
                <KbCreateMenu
                    @create-folder="emit('createFolder')"
                    @create-markdown="emit('createMarkdown')"
                    @upload="emit('upload')"
                    @youtube="emit('youtube')"
                    @link="emit('link')"
                    @import-document="emit('importDocument')"
                />
                <IconButton
                    v-if="items.length > 0"
                    :icon="['fas', selecting ? 'xmark' : 'square-check']"
                    :label="selecting ? t('kb.stopSelecting') : t('kb.startSelecting')"
                    class="ml-auto"
                    data-testid="kb-toggle-selecting"
                    @click="emit('toggleSelecting')"
                />
            </div>

            <KbSelectionBar
                :selected-count="selectedCount"
                class="mt-3"
                @move="emit('moveSelection')"
                @tag="emit('tagSelection')"
                @clear="emit('clearSelection')"
            />

            <KbItemGrid
                v-if="items.length > 0 && viewMode === 'grid'"
                data-onboarding="knowledge.first-entry"
                :items="items"
                :selecting="selecting"
                :selected-keys="selectedKeys"
                @toggle-select="(key, value, shift) => emit('toggleSelect', key, value, shift)"
            />
            <KbItemList
                v-else-if="items.length > 0"
                data-onboarding="knowledge.first-entry"
                :items="items"
                :selecting="selecting"
                :selected-keys="selectedKeys"
                @toggle-select="(key, value, shift) => emit('toggleSelect', key, value, shift)"
            />

            <p v-else class="text-[var(--text-muted)] text-center py-8">
                {{ t('kb.emptyFolder') }}
            </p>
        </template>
    </div>
</template>
