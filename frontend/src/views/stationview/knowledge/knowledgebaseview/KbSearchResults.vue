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
import SubHeader from '@/components/typography/SubHeader.vue'
import KbItemActions from './KbItemActions.vue'
import type {KbItem} from './useKbItems'

const {t} = useI18n()

defineProps<{
    items: KbItem[]
    searching: boolean
    totalCount: number
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
                v-for="item in items"
                :key="item.key"
                class="hover:border-[var(--primary)] transition-colors"
                :class="item.open ? 'cursor-pointer' : ''"
                @click="item.open?.()"
            >
                <div class="flex items-start gap-3 p-2">
                    <font-awesome-icon :icon="item.icon" class="text-xl mt-0.5" :class="item.iconClass"/>
                    <div class="flex-1 min-w-0">
                        <div class="flex items-center gap-2">
                            <span class="text-sm font-medium">{{ item.title }}</span>
                            <StationBadge v-if="item.stationName" :station-name="item.stationName"/>
                        </div>
                        <p
                            v-if="item.snippet"
                            class="search-snippet text-xs text-[var(--text-muted)] mt-1 line-clamp-2"
                            v-html="item.snippet"
                        />
                        <p v-else-if="item.description" class="text-xs text-[var(--text-muted)] mt-1 truncate">
                            {{ item.description }}
                        </p>
                    </div>
                    <KbItemActions :actions="item.actions"/>
                </div>
            </NeutralContainer>
        </div>
    </div>
</template>
