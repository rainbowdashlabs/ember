/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import { boards } from '@/api'
import type { Board } from '@/api/boards'
import { useConfigPanel } from '@/composables/useConfigPanel'

const { t } = useI18n()
const router = useRouter()

const { config: boardList, loading, error } = useConfigPanel<Board[]>({
    initial: [],
    fetch: () => boards.listBoards(true),
})
</script>

<template>
    <ViewContent
        :title="t('pages.board-overview.title')"
        :subtitle="t('pages.board-overview.subtitle')"
    >
        <AsyncSection
            :empty="boardList.length === 0"
            :empty-message="t('boards.noBoards')"
            :error="error"
            :loading="loading"
        >
            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                <NeutralContainer
                    v-for="board in boardList"
                    :key="board.id"
                    class="cursor-pointer hover:border-[var(--accent)] transition-colors"
                    @click="router.push(`/station/boards/${board.shortKey}`)"
                >
                    <div class="flex items-center gap-2 mb-1">
                        <span class="text-xs font-mono text-[var(--text-muted)] bg-[var(--bg-muted)] px-1.5 py-0.5 rounded">{{ board.shortKey }}</span>
                        <SubHeader>{{ board.name }}</SubHeader>
                    </div>
                    <p v-if="board.description" class="text-sm text-[var(--text-muted)] line-clamp-2">{{ board.description }}</p>
                    <div class="mt-3 text-xs text-[var(--text-muted)]">
                        {{ board.ticketCounter }} {{ board.ticketCounter === 1 ? 'Ticket' : 'Tickets' }}
                    </div>
                </NeutralContainer>
            </div>
        </AsyncSection>
    </ViewContent>
</template>
