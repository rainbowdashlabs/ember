/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import IconButton from '@/components/button/IconButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {KbFile, SearchResult} from '@/api/knowledgeBase'
import {fileIcon} from '@/util/kbFileIcon'

const {t} = useI18n()

defineProps<{
    results: SearchResult[]
    searching: boolean
    totalCount: number
}>()

const emit = defineEmits<{
    navigateFile: [file: KbFile]
    copySharedFile: [id: number]
}>()

</script>

<template>
    <div>
        <SubHeader class="mb-3">{{ t('kb.searchResults') }}</SubHeader>
        <Spinner v-if="searching"/>
        <p v-else-if="totalCount === 0" class="text-[var(--text-muted)]">
            {{ t('kb.noResults') }}
        </p>
        <div v-else class="flex flex-col gap-2">
            <NeutralContainer
                v-for="result in results"
                :key="result.file.id + '-' + (result.stationName || 'local')"
                class="cursor-pointer hover:border-[var(--primary)] transition-colors"
                @click="emit('navigateFile', result.file)"
            >
                <div class="flex items-start gap-3 p-2">
                    <font-awesome-icon :icon="fileIcon(result.file)" class="text-xl text-[var(--primary)] mt-0.5"/>
                    <div class="flex-1 min-w-0">
                        <div class="flex items-center gap-2">
                            <span class="text-sm font-medium">{{ result.file.name }}</span>
                            <StationBadge v-if="result.stationName" :station-name="result.stationName" />
                        </div>
                        <p v-if="result.snippet" class="search-snippet text-xs text-[var(--text-muted)] mt-1 line-clamp-2" v-html="result.snippet"/>
                        <p v-else-if="result.file.description" class="text-xs text-[var(--text-muted)] mt-1 truncate">
                            {{ result.file.description }}
                        </p>
                    </div>
                    <IconButton
                        v-if="result.stationName"
                        :icon="['fas', 'copy']"
                        :label="t('federation.copyToStation')"
                        @click.stop="emit('copySharedFile', result.file.id)"
                    />
                </div>
            </NeutralContainer>
        </div>
    </div>
</template>
