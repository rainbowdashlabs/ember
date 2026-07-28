/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import { contrastTextColor } from '@/theme/contrast'
import { priorityIcon, priorityColor } from '@/util/ticketPriority'
import type { BoardLabel, BoardTicket } from '@/api/boards'

defineProps<{
    result: BoardTicket
    shortKey: string
    labels: BoardLabel[]
    laneName: string
}>()

const emit = defineEmits<{
    select: []
}>()
</script>

<template>
    <div
        class="px-3 py-2 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2"
        @click="emit('select')"
    >
        <div class="flex-1 min-w-0">
            <div class="flex items-center gap-1.5">
                <span class="font-mono text-xs text-(--text-muted) shrink-0">{{ shortKey }}-{{ result.ticketNumber }}</span>
                <span class="truncate">{{ result.title }}</span>
            </div>
            <div v-if="labels.length > 0" class="flex flex-wrap gap-1 mt-0.5">
                <BaseBadge v-for="label in labels" :key="label.id" bg-class="" :style="{ backgroundColor: label.color, color: contrastTextColor(label.color) }">{{ label.name }}</BaseBadge>
            </div>
        </div>
        <div class="flex items-center gap-2 shrink-0 text-xs text-(--text-muted)">
            <span class="px-1.5 py-0.5 rounded bg-(--bg-accent) text-[0.65rem]">{{ laneName }}</span>
            <font-awesome-icon :icon="priorityIcon(result.priority)" :class="priorityColor(result.priority)" />
            <UserAvatar v-if="result.assignee" :identity="result.assignee" size="sm" />
        </div>
    </div>
</template>
