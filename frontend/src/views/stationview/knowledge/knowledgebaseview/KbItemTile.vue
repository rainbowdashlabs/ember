/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import AuthImage from '@/components/display/AuthImage.vue'
import KbItemActions from './KbItemActions.vue'
import type {KbItem} from './useKbItems'

defineProps<{
    item: KbItem
}>()
</script>

<template>
    <NeutralContainer
        data-testid="kb-item"
        class="hover:border-[var(--primary)] transition-colors relative group"
        :class="item.open ? 'cursor-pointer' : ''"
        @click="item.open?.()"
    >
        <div class="flex flex-col items-center gap-2 p-2 text-center">
            <AuthImage
                v-if="item.imageUrl"
                :src="item.imageUrl"
                :alt="item.title"
                class="w-8 h-8 rounded object-cover"
            >
                <template #error>
                    <font-awesome-icon :icon="item.icon" class="text-2xl" :class="item.iconClass"/>
                </template>
            </AuthImage>
            <font-awesome-icon v-else :icon="item.icon" class="text-2xl" :class="item.iconClass"/>

            <div class="flex items-center justify-center gap-1 w-full">
                <span class="text-sm font-medium truncate">{{ item.title }}</span>
                <font-awesome-icon
                    v-if="item.restricted"
                    :icon="['fas', 'lock']"
                    class="ml-1 h-3 w-3 text-[var(--text-muted)] flex-shrink-0"
                />
            </div>

            <StationBadge v-if="item.stationName" :station-name="item.stationName"/>
            <span
                v-if="item.levelLabel"
                class="text-[10px] text-[var(--text-muted)] border border-[var(--border)] rounded-full px-2 py-0.5"
            >
                {{ item.levelLabel }}
            </span>

            <span v-if="item.countLabel" class="text-[10px] text-[var(--text-muted)]">{{ item.countLabel }}</span>
            <span v-if="item.description" class="text-xs text-[var(--text-muted)] truncate w-full">
                {{ item.description }}
            </span>
        </div>

        <div v-if="item.favourite" class="absolute top-1 left-1 flex gap-1">
            <font-awesome-icon :icon="['fas', 'star']" class="text-xs text-yellow-500"/>
        </div>

        <div class="absolute top-1 right-1">
            <KbItemActions :actions="item.actions" hover-group/>
        </div>
    </NeutralContainer>
</template>
