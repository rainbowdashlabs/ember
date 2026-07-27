/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, onBeforeUnmount, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useBreakpoint} from '@/composables/useBreakpoint'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import IconButton from '@/components/button/IconButton.vue'

defineProps<{
    isFirst?: boolean
    isLast?: boolean
    canPasteCell?: boolean
    label?: string
}>()

const emit = defineEmits<{
    copy: []
    cut: []
    delete: []
    'move-up': []
    'move-down': []
    'paste-cell': []
}>()

const {t} = useI18n()
const {isMobile} = useBreakpoint()

const open = ref(false)
const rootRef = ref<HTMLElement | null>(null)

const triggerVisibility = computed(() =>
    isMobile.value ? 'opacity-100' : 'opacity-0 group-hover:opacity-100 focus-within:opacity-100')

function toggle(e: MouseEvent) {
    e.stopPropagation()
    open.value = !open.value
}

function close() { open.value = false }

function onDocClick(e: MouseEvent) {
    if (!rootRef.value) return
    if (!rootRef.value.contains(e.target as Node)) close()
}

if (typeof document !== 'undefined') {
    document.addEventListener('click', onDocClick)
    onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
}

defineExpose({close})
</script>

<template>
    <div ref="rootRef" class="absolute top-1 right-1 z-10">
        <IconButton
            :icon="['fas', 'ellipsis']"
            :label="t('stationPages.editor.rowMenu')"
            class="w-7 h-7 !p-0 bg-(--bg)/80 backdrop-blur-sm border border-(--border) text-(--text-muted) hover:text-(--text) hover:border-primary shadow-sm"
            :class="triggerVisibility"
            @click="toggle"
        />
        <div
            v-if="open"
            class="absolute top-full right-0 mt-1 min-w-44 rounded-theme border border-(--border) bg-(--bg) shadow-lg py-1"
        >
            <p v-if="label" class="px-3 py-1 text-[10px] uppercase tracking-wider text-(--text-muted)">{{ label }}</p>
            <DropdownMenuItem
                v-if="!isFirst"
                :icon="['fas', 'angle-up']"
                @click="emit('move-up'); close()"
            >{{ t('common.moveUp') }}</DropdownMenuItem>
            <DropdownMenuItem
                v-if="!isLast"
                :icon="['fas', 'angle-down']"
                @click="emit('move-down'); close()"
            >{{ t('common.moveDown') }}</DropdownMenuItem>
            <div v-if="!isFirst || !isLast" class="border-t border-(--border) my-1"/>
            <DropdownMenuItem :icon="['fas', 'copy']" @click="emit('copy'); close()">{{ t('stationPages.editor.copyRow') }}</DropdownMenuItem>
            <DropdownMenuItem :icon="['fas', 'scissors']" @click="emit('cut'); close()">{{ t('stationPages.editor.cutRow') }}</DropdownMenuItem>
            <DropdownMenuItem
                v-if="canPasteCell"
                :icon="['fas', 'paste']"
                class="text-primary"
                @click="emit('paste-cell'); close()"
            >{{ t('stationPages.editor.pasteCell') }}</DropdownMenuItem>
            <div class="border-t border-(--border) my-1"/>
            <DropdownMenuItem :icon="['fas', 'trash']" class="text-error" @click="emit('delete'); close()">{{ t('common.delete') }}</DropdownMenuItem>
        </div>
    </div>
</template>
