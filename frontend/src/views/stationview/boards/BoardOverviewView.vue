/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import BoardCard from './BoardCard.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import { boards } from '@/api'
import type { Board } from '@/api/generated/schema'
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
                    <BoardCard :board="board" />
                </RowLink>
            </div>
        </AsyncSection>
    </ViewContent>
</template>
