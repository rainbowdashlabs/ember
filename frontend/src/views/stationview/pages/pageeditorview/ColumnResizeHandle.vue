/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, onBeforeUnmount} from 'vue'
import {useI18n} from 'vue-i18n'

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

const dragging = ref(false)
let startX = 0
let containerWidth = 0

function onMouseDown(event: MouseEvent) {
    event.preventDefault()
    dragging.value = true
    startX = event.clientX
    const container = (event.target as HTMLElement).closest('.editor-row-cells')
    containerWidth = container?.getBoundingClientRect().width ?? 1
    // Lock the entire document to the col-resize cursor while dragging so the pointer doesn't
    // flicker back to default when it leaves the narrow handle.
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'

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
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
}

onBeforeUnmount(onMouseUp)
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
