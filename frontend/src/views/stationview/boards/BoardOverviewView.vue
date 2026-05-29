/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import { boards } from '@/api'
import type { Board } from '@/api/boards'

const { t } = useI18n()
const router = useRouter()

const boardList = ref<Board[]>([])
const loading = ref(true)
const error = ref('')

async function loadBoards() {
    loading.value = true
    error.value = ''
    try {
        boardList.value = await boards.listBoards(true)
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
}

onMounted(loadBoards)
</script>

<template>
    <ViewContent>
        <SectionHeader class="mb-6">{{ t('boards.title') }}</SectionHeader>

        <Spinner v-if="loading" />
        <Alert v-else-if="error" variant="error">{{ error }}</Alert>
        <EmptyState v-else-if="boardList.length === 0">{{ t('boards.noBoards') }}</EmptyState>

        <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
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
    </ViewContent>
</template>
