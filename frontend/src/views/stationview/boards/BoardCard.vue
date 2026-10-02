/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { Board } from '@/api/generated/schema'

/**
 * One board in a list of boards: its short key, name, description and ticket count. A manageable card
 * also offers the settings and the delete button.
 */
withDefaults(defineProps<{
    board: Pick<Board, 'shortKey' | 'name' | 'description' | 'ticketCounter'>
    manageable?: boolean
}>(), {
    manageable: false,
})

const emit = defineEmits<{
    settings: []
    delete: []
}>()

const { t } = useI18n()
</script>

<template>
    <NeutralContainer class="h-full cursor-pointer hover:border-[var(--accent)] transition-colors">
        <div class="flex items-start justify-between">
            <div>
                <div class="flex items-center gap-2 mb-1">
                    <span class="text-xs font-mono text-[var(--text-muted)] bg-[var(--bg-muted)] px-1.5 py-0.5 rounded">{{ board.shortKey }}</span>
                    <SubHeader>{{ board.name }}</SubHeader>
                </div>
                <p v-if="board.description" class="text-sm text-[var(--text-muted)] line-clamp-2">{{ board.description }}</p>
            </div>
            <div v-if="manageable" class="flex items-center gap-1 shrink-0">
                <IconButton :icon="['fas', 'gears']" :label="t('boards.settings')" @click="emit('settings')" />
                <DeleteButton @click="emit('delete')" />
            </div>
        </div>
        <div class="mt-3 text-xs text-[var(--text-muted)]">
            {{ t(board.ticketCounter === 1 ? 'boards.ticketCountOne' : 'boards.ticketCountMany', {count: board.ticketCounter}) }}
        </div>
    </NeutralContainer>
</template>
