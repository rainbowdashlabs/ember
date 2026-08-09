/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import IconButton from '@/components/button/IconButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import {type DiscoveredBoard, BoardShareMode} from '@/api/federatedBoards'

const props = defineProps<{
    board: DiscoveredBoard
    bookmarked: boolean
}>()

const emit = defineEmits<{
    'toggle-bookmark': [board: DiscoveredBoard]
    'navigate': [board: DiscoveredBoard]
}>()

const {t} = useI18n()

function truncate(text: string, maxLength: number): string {
    if (text.length <= maxLength) return text
    return text.slice(0, maxLength) + '...'
}
</script>

<template>
    <NeutralContainer
        class="cursor-pointer hover:ring-2 hover:ring-primary transition-all"
        @click="emit('navigate', board)"
    >
        <div class="flex items-start justify-between gap-2">
            <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2 flex-wrap">
                    <span class="font-semibold">{{ board.name }}</span>
                    <code class="text-xs font-mono bg-bg-light-accent dark:bg-bg-dark-accent px-1.5 py-0.5 rounded">{{ board.shortKey }}</code>
                </div>
                <p v-if="board.description" class="text-sm mt-1 text-text-light-secondary dark:text-text-dark-secondary">
                    {{ truncate(board.description, 120) }}
                </p>
            </div>
            <IconButton
                :icon="['fas', 'star']"
                :label="bookmarked ? t('boards.bookmarked') : t('boards.bookmark')"
                :class="bookmarked
                    ? 'text-yellow-500 hover:text-yellow-600'
                    : 'text-text-light-secondary dark:text-text-dark-secondary hover:text-yellow-500'"
                @click.stop="emit('toggle-bookmark', board)"
            />
        </div>
        <div class="flex items-center gap-2 mt-3">
            <InfoBadge v-if="board.shareMode === BoardShareMode.READ_ONLY" class="inline-flex items-center gap-1">
                <font-awesome-icon :icon="['fas', 'lock']" class="text-[0.65rem]"/>
                {{ t('boards.readOnlyBadge') }}
            </InfoBadge>
            <SuccessBadge v-else-if="board.shareMode === BoardShareMode.FULL" class="inline-flex items-center gap-1">
                <font-awesome-icon :icon="['fas', 'pen']" class="text-[0.65rem]"/>
                {{ t('boards.fullAccessBadge') }}
            </SuccessBadge>
        </div>
    </NeutralContainer>
</template>
