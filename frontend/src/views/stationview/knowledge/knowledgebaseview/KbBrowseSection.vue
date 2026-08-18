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
import type {KbFolder} from '@/api/knowledgeBase'
import type {KbItem} from './useKbItems'

const {t} = useI18n()

defineProps<{
    loading: boolean
    currentFolder: KbFolder | null
    viewMode: 'grid' | 'list'
    items: KbItem[]
    canManage: boolean
}>()

const emit = defineEmits<{
    createFolder: []
    createMarkdown: []
    upload: []
    youtube: []
    link: []
    importDocument: []
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

            <KbItemGrid v-if="items.length > 0 && viewMode === 'grid'" :items="items"/>
            <KbItemList v-else-if="items.length > 0" :items="items"/>

            <p v-else class="text-[var(--text-muted)] text-center py-8">
                {{ t('kb.emptyFolder') }}
            </p>
        </template>
    </div>
</template>
