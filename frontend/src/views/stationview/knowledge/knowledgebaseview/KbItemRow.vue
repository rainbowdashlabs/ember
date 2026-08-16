/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import StationBadge from '@/components/badge/StationBadge.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import AuthImage from '@/components/display/AuthImage.vue'
import KbItemActions from './KbItemActions.vue'
import {formatDate} from '@/util/format'
import type {KbItem} from './useKbItems'

defineProps<{
    item: KbItem
}>()
</script>

<template>
    <div
        class="flex items-center gap-2 px-3 py-1.5 hover:bg-[var(--bg-accent)] transition-colors group"
        :class="item.open ? 'cursor-pointer' : ''"
        @click="item.open?.()"
    >
        <div class="w-5 flex-shrink-0 flex justify-center">
            <AuthImage
                v-if="item.imageUrl"
                :src="item.imageUrl"
                :alt="item.title"
                class="w-4 h-4 rounded object-cover"
            >
                <template #error>
                    <font-awesome-icon :icon="item.icon" class="text-sm" :class="item.iconClass"/>
                </template>
            </AuthImage>
            <font-awesome-icon v-else :icon="item.icon" class="text-sm" :class="item.iconClass"/>
        </div>

        <font-awesome-icon
            v-if="item.favourite"
            :icon="['fas', 'star']"
            class="text-xs text-yellow-500 flex-shrink-0"
        />

        <span class="text-sm font-medium truncate min-w-0 flex-1">{{ item.title }}</span>
        <MutedIcon v-if="item.restricted" :icon="['fas', 'lock']" class="flex-shrink-0 ml-1"/>
        <StationBadge v-if="item.stationName" :station-name="item.stationName"/>

        <span v-if="item.countLabel" class="text-xs text-[var(--text-muted)]">{{ item.countLabel }}</span>
        <span
            v-else-if="item.description"
            class="hidden sm:block text-xs text-[var(--text-muted)] truncate max-w-48"
        >
            {{ item.description }}
        </span>
        <span class="hidden md:block text-xs text-[var(--text-muted)] w-16 text-right flex-shrink-0">
            {{ item.typeLabel }}
        </span>
        <span class="hidden md:block text-xs text-[var(--text-muted)] w-24 text-right flex-shrink-0">
            {{ item.updatedAt ? formatDate(item.updatedAt) : '' }}
        </span>

        <KbItemActions :actions="item.actions" hover-group/>
    </div>
</template>
