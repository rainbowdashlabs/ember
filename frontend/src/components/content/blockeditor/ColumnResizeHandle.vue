/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useColumnResize} from './useColumnResize'

const props = withDefaults(defineProps<{
    leftPercent: number
    rightPercent: number
    compact?: boolean
}>(), {
    compact: false,
})

const emit = defineEmits<{
    resize: [leftDelta: number]
}>()

const {t} = useI18n()

const {dragging, onMouseDown} = useColumnResize(
    computed(() => props.leftPercent),
    computed(() => props.rightPercent),
    delta => emit('resize', delta),
)
</script>

<template>
    <div
        class="flex-shrink-0 self-stretch cursor-col-resize flex items-center justify-center rounded-sm group select-none"
        :class="[compact ? 'w-3 min-h-6' : 'w-5 min-h-8', dragging ? 'bg-primary/20' : 'hover:bg-primary/10']"
        :title="t('stationPages.editor.resizeColumn')"
        role="separator"
        @mousedown="onMouseDown"
    >
        <font-awesome-icon
            :icon="['fas', 'grip-vertical']"
            class="transition-colors pointer-events-none"
            :class="[compact ? 'h-3 w-3' : 'h-5 w-5', dragging ? 'text-primary' : 'text-(--text) group-hover:text-primary']"
        />
    </div>
</template>
