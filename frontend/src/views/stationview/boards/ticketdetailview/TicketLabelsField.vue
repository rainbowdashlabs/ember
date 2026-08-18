/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import FieldLabel from '@/components/typography/FieldLabel.vue'
import LabelSelectInput from '@/components/input/select/LabelSelectInput.vue'
import { contrastTextColor } from '@/theme/contrast'
import type { BoardLabel } from '@/api/boards'

defineProps<{
    allLabels: BoardLabel[]
    ticketLabels: BoardLabel[]
    canEdit: boolean
}>()

const emit = defineEmits<{
    toggle: [id: number]
    create: [name: string]
}>()
</script>

<template>
    <div v-if="allLabels.length > 0">
        <FieldLabel class="mb-1">Labels</FieldLabel>
        <LabelSelectInput v-if="canEdit" :labels="allLabels" :selected="ticketLabels" @toggle="emit('toggle', $event)" @create="emit('create', $event)" />
        <div v-else class="flex flex-wrap gap-1">
            <BaseBadge v-for="label in ticketLabels" :key="label.id" bg-class="" :style="{ backgroundColor: label.color, color: contrastTextColor(label.color) }">{{ label.name }}</BaseBadge>
            <span v-if="ticketLabels.length === 0" class="text-sm text-(--text-muted)">-</span>
        </div>
    </div>
</template>
