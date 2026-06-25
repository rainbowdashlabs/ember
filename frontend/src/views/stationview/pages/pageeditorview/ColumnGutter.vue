/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onBeforeUnmount, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import EditorFloatButton from './EditorFloatButton.vue'

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
const dragging = ref(false)
let startX = 0
let containerWidth = 0

function onResizeStart(event: MouseEvent) {
    event.preventDefault()
    dragging.value = true
    startX = event.clientX
    const container = (event.currentTarget as HTMLElement).closest('.editor-row-cells')
    containerWidth = container?.getBoundingClientRect().width ?? 1
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
    document.addEventListener('mousemove', onResizeMove)
    document.addEventListener('mouseup', onResizeEnd)
}

function onResizeMove(event: MouseEvent) {
    if (!dragging.value) return
    const deltaX = event.clientX - startX
    const deltaPercent = (deltaX / containerWidth) * 100
    if (props.leftPercent + deltaPercent < 10 || props.rightPercent - deltaPercent < 10) return
    startX = event.clientX
    emit('resize', deltaPercent)
}

function onResizeEnd() {
    dragging.value = false
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    document.removeEventListener('mousemove', onResizeMove)
    document.removeEventListener('mouseup', onResizeEnd)
}

onBeforeUnmount(onResizeEnd)
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
