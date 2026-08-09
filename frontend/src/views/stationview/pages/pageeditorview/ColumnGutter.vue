/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import EditorFloatButton from './EditorFloatButton.vue'
import {useColumnResize} from './useColumnResize'

const props = withDefaults(defineProps<{
    leftPercent: number
    rightPercent: number
    canAddColumn?: boolean
}>(), {
    canAddColumn: true,
})

const emit = defineEmits<{
    resize: [leftDelta: number]
    swap: []
    'add-column': []
}>()

const {t} = useI18n()

const {dragging, onMouseDown: onResizeStart} = useColumnResize(
    computed(() => props.leftPercent),
    computed(() => props.rightPercent),
    delta => emit('resize', delta),
)
</script>

<template>
    <div class="relative flex flex-col items-center justify-center self-stretch gap-1.5 px-1 z-10">
        <IconButton
            :icon="['fas', 'grip-vertical']"
            :label="t('stationPages.editor.resizeColumn')"
            class="rounded-full bg-(--bg) border border-(--border) text-(--text-muted) hover:text-primary hover:border-primary shadow-sm !p-1 text-xs cursor-col-resize"
            :class="dragging ? '!text-primary !border-primary !bg-primary/10' : ''"
            @mousedown.prevent.stop="onResizeStart"
            @click.prevent.stop
        />
        <EditorFloatButton
            :icon="['fas', 'arrow-right-arrow-left']"
            :label="t('stationPages.editor.swapCells')"
            @click="emit('swap')"
        />
        <EditorFloatButton
            v-if="canAddColumn"
            :icon="['fas', 'plus']"
            :label="t('stationPages.editor.addColumn')"
            @click="emit('add-column')"
        />
    </div>
</template>
