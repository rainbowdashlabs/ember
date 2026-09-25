/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import { boards } from '@/api'
import type { Board } from '@/api/boards'
import { useConfigPanel } from '@/composables/useConfigPanel'

const { t } = useI18n()

const { config: boardList, loading, failure } = useConfigPanel<Board[]>({
    initial: [],
    fetch: () => boards.listBoards(true),
})

function boardPage(board: Board): string {
    return `/station/boards/${board.shortKey}`
}
</script>

<template>
    <ViewContent
        :title="t('pages.board-overview.title')"
        :subtitle="t('pages.board-overview.subtitle')"
    >
        <AsyncSection
            :empty="boardList.length === 0"
            :empty-message="t('boards.noBoards')"
            :failure="failure"
            :loading="loading"
        >
            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                <RowLink v-for="board in boardList" :key="board.id" :to="boardPage(board)">
                    <NeutralContainer class="h-full cursor-pointer hover:border-[var(--accent)] transition-colors">
                        <div class="flex items-center gap-2 mb-1">
                            <span class="text-xs font-mono text-[var(--text-muted)] bg-[var(--bg-muted)] px-1.5 py-0.5 rounded">{{ board.shortKey }}</span>
                            <SubHeader>{{ board.name }}</SubHeader>
                        </div>
                        <p v-if="board.description" class="text-sm text-[var(--text-muted)] line-clamp-2">{{ board.description }}</p>
                        <div class="mt-3 text-xs text-[var(--text-muted)]">
                            {{ board.ticketCounter }} {{ board.ticketCounter === 1 ? 'Ticket' : 'Tickets' }}
                        </div>
                    </NeutralContainer>
                </RowLink>
            </div>
        </AsyncSection>
    </ViewContent>
</template>
