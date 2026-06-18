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

const props = withDefaults(defineProps<{
    /** Cell width as percent. Editable from inside the menu. */
    widthPercent?: number | null
    /** True when the surrounding row has more than one cell (so width and split make sense). */
    canResize?: boolean
    /** True when the clipboard currently holds a cell that can be pasted over this one. */
    canPaste?: boolean
    /** Optional label shown at the top of the menu (e.g. the cell type). */
    label?: string
}>(), {
    canResize: false,
    canPaste: false,
})

const emit = defineEmits<{
    copy: []
    cut: []
    paste: []
    delete: []
    split: [columns: number]
    'update:widthPercent': [value: number]
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

function commitWidth(value: string | number) {
    const v = typeof value === 'number' ? value : Number(value)
    if (!Number.isFinite(v)) return
    const clamped = Math.max(10, Math.min(100, Math.round(v)))
    emit('update:widthPercent', clamped)
}

function doSplit(n: number) {
    emit('split', n)
    close()
}

if (typeof document !== 'undefined') {
    document.addEventListener('click', onDocClick)
    onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
}

defineExpose({close})
</script>

<template>
    <div ref="rootRef" class="absolute top-1 right-1 z-10">
        <button
            type="button"
            class="w-7 h-7 rounded-theme bg-(--bg)/80 backdrop-blur-sm border border-(--border) text-(--text-muted) hover:text-(--text) hover:border-primary flex items-center justify-center shadow-sm transition-all"
            :class="triggerVisibility"
            :title="t('stationPages.editor.cellMenu')"
            @click="toggle"
        >
            <font-awesome-icon :icon="['fas', 'ellipsis']"/>
        </button>
        <div
            v-if="open"
            class="absolute top-full right-0 mt-1 min-w-44 rounded-theme border border-(--border) bg-(--bg) shadow-lg py-1"
        >
            <p v-if="label" class="px-3 py-1 text-[10px] uppercase tracking-wider text-(--text-muted)">{{ label }}</p>
            <div v-if="canResize" class="px-3 py-1.5 flex items-center justify-between gap-2 text-sm">
                <span class="text-(--text-muted)">{{ t('stationPages.editor.width') }} %</span>
                <input
                    type="number"
                    min="10"
                    max="100"
                    step="1"
                    :value="Math.round(widthPercent ?? 0)"
                    class="w-20 px-2 py-1 rounded border border-(--border) bg-(--bg) text-(--text) text-xs text-right"
                    @click.stop
                    @change="(e: Event) => commitWidth((e.target as HTMLInputElement).value)"
                />
            </div>
            <div class="border-t border-(--border) my-1"/>
            <DropdownMenuItem :icon="['fas', 'copy']" @click="emit('copy'); close()">{{ t('stationPages.editor.copyCell') }}</DropdownMenuItem>
            <DropdownMenuItem :icon="['fas', 'scissors']" @click="emit('cut'); close()">{{ t('stationPages.editor.cutCell') }}</DropdownMenuItem>
            <DropdownMenuItem v-if="canPaste" :icon="['fas', 'paste']" @click="emit('paste'); close()">{{ t('stationPages.editor.pasteCell') }}</DropdownMenuItem>
            <DropdownMenuItem :icon="['fas', 'table-columns']" @click="doSplit(2)">{{ t('stationPages.editor.splitCell') }}</DropdownMenuItem>
            <div class="border-t border-(--border) my-1"/>
            <DropdownMenuItem :icon="['fas', 'trash']" class="text-error" @click="emit('delete'); close()">{{ t('common.delete') }}</DropdownMenuItem>
        </div>
    </div>
</template>
