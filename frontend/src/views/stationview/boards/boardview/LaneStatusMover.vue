/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, computed } from 'vue'
import { contrastTextColor } from '@/theme/contrast'
import type { BoardLane } from '@/api/boards'

const props = defineProps<{ lanes: BoardLane[]; currentLaneId: number | undefined; canEdit: boolean }>()
const emit = defineEmits<{ (e: 'move', laneId: number): void }>()

const editing = ref(false)
const currentLane = computed(() => props.lanes.find(l => l.id === props.currentLaneId))
const currentIndex = computed(() => props.currentLaneId == null ? -1 : props.lanes.findIndex(l => l.id === props.currentLaneId))
const choices = computed(() => {
    const i = currentIndex.value
    if (i < 0) return []
    const out: BoardLane[] = []
    const next = props.lanes[i + 1]
    const previous = i > 0 ? props.lanes[i - 1] : undefined
    if (next) out.push(next)
    if (previous) out.push(previous)
    return out
})
function isNext(laneId: number): boolean { return props.lanes.findIndex(l => l.id === laneId) > currentIndex.value }
function toggle() { if (props.canEdit) editing.value = !editing.value }
function pick(laneId: number) { emit('move', laneId); editing.value = false }
defineExpose({ close: () => { editing.value = false } })
</script>

<template>
    <div class="relative">
        <div class="px-3 py-2 rounded-theme text-sm font-medium text-center" :class="canEdit ? 'cursor-pointer' : ''" :style="{ backgroundColor: currentLane?.color ?? 'var(--primary)', color: contrastTextColor(currentLane?.color ?? '#fd4f00') }" @click.stop="toggle">
            <span>{{ currentLane?.name }}</span>
            <font-awesome-icon v-if="canEdit" :icon="['fas', editing ? 'chevron-up' : 'chevron-down']" class="ms-2 text-xs opacity-80"/>
        </div>
        <div v-if="editing && canEdit" class="absolute z-20 mt-1 w-full rounded-theme border border-[var(--border)] bg-[var(--bg)] shadow-lg overflow-hidden">
            <div v-for="lane in choices" :key="lane.id" class="px-3 py-2 text-sm font-medium text-center cursor-pointer hover:opacity-90 flex items-center justify-center gap-2" :style="{ backgroundColor: lane.color ?? 'var(--primary)', color: contrastTextColor(lane.color ?? '#fd4f00') }" @click="pick(lane.id)">
                <font-awesome-icon :icon="['fas', isNext(lane.id) ? 'arrow-right' : 'arrow-left']" class="text-xs opacity-80"/>
                <span>{{ lane.name }}</span>
            </div>
        </div>
    </div>
</template>
