/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'

const props = defineProps<{
    leftPercent: number
    rightPercent: number
}>()

const emit = defineEmits<{
    resize: [leftDelta: number]
}>()

const dragging = ref(false)
let startX = 0
let containerWidth = 0

function onMouseDown(event: MouseEvent) {
    event.preventDefault()
    dragging.value = true
    startX = event.clientX
    const container = (event.target as HTMLElement).closest('.editor-row-cells')
    containerWidth = container?.getBoundingClientRect().width ?? 1

    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)
}

function onMouseMove(event: MouseEvent) {
    if (!dragging.value) return
    const deltaX = event.clientX - startX
    const deltaPercent = (deltaX / containerWidth) * 100

    if (props.leftPercent + deltaPercent < 10 || props.rightPercent - deltaPercent < 10) {
        return
    }

    startX = event.clientX
    emit('resize', deltaPercent)
}

function onMouseUp() {
    dragging.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
}
</script>

<template>
    <div
        class="flex-shrink-0 w-2 cursor-col-resize flex items-center justify-center group"
        :class="dragging ? 'bg-primary/20' : 'hover:bg-primary/10'"
        @mousedown="onMouseDown"
    >
        <div
            class="w-0.5 h-8 rounded-full transition-colors"
            :class="dragging ? 'bg-primary' : 'bg-[var(--border)] group-hover:bg-primary/60'"
        />
    </div>
</template>
