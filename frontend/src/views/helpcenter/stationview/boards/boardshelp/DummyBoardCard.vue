/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'

defineProps<{
    prefix: string
    name: string
    description: string
    ticketCount: string
    showManagerActions: boolean
}>()

const {t} = useI18n()
</script>

<template>
    <NeutralContainer class="cursor-pointer hover:border-[var(--accent)] transition-colors">
        <div class="flex items-start justify-between">
            <div>
                <div class="flex items-center gap-2 mb-1">
                    <span class="text-xs font-mono text-[var(--text-muted)] bg-[var(--bg-muted)] px-1.5 py-0.5 rounded">{{ prefix }}</span>
                    <SubHeader>{{ name }}</SubHeader>
                </div>
                <p class="text-sm text-[var(--text-muted)] line-clamp-2">{{ description }}</p>
            </div>
            <div v-if="showManagerActions" class="flex items-center gap-1 shrink-0">
                <IconButton :icon="['fas', 'gears']" label="Settings" />
                <DeleteButton />
            </div>
        </div>
        <div class="mt-3 text-xs text-[var(--text-muted)]">{{ ticketCount }}</div>
    </NeutralContainer>
</template>
