/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'
import {useEditorActionsMenu} from './useEditorActionsMenu'
import BaseInput from '@/components/input/BaseInput.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import IconButton from '@/components/button/IconButton.vue'

/** Cell width as percent. Editable from inside the menu. */
const widthPercent = defineModel<number | null>('widthPercent')

withDefaults(defineProps<{
    /** True when the surrounding row has more than one cell (so width and split make sense). */
    canResize?: boolean
    /** True when the clipboard currently holds a cell that can be pasted over this one. */
    canPaste?: boolean
    /** Optional label shown at the top of the menu (e.g. the cell type). */
    label?: string
    /** True where the editor lets a block be shown to some members only, which a letter does. */
    restrictable?: boolean
}>(), {
    canResize: false,
    canPaste: false,
    label: undefined,
    restrictable: false,
})

const emit = defineEmits<{
    copy: []
    cut: []
    paste: []
    delete: []
    split: [columns: number]
    visibility: []
}>()

const {t} = useI18n()
const widthId = useId()

const {open, rootRef, triggerVisibility, toggle, close} = useEditorActionsMenu()

function commitWidth(value: string | number) {
    const v = typeof value === 'number' ? value : Number(value)
    if (!Number.isFinite(v)) return
    const clamped = Math.max(10, Math.min(100, Math.round(v)))
    widthPercent.value = clamped
}

function doSplit(n: number) {
    emit('split', n)
    close()
}

defineExpose({close})
</script>

<template>
    <div ref="rootRef" class="absolute top-1 right-1 z-10">
        <IconButton
            :icon="['fas', 'ellipsis']"
            :label="t('stationPages.editor.cellMenu')"
            class="w-7 h-7 !p-0 bg-(--bg)/80 backdrop-blur-sm border border-(--border) text-(--text-muted) hover:text-(--text) hover:border-primary shadow-sm"
            :class="triggerVisibility"
            @click="toggle"
        />
        <div
            v-if="open"
            class="absolute top-full right-0 mt-1 min-w-44 rounded-theme border border-(--border) bg-(--bg) shadow-lg py-1"
        >
            <p v-if="label" class="px-3 py-1 text-[10px] uppercase tracking-wider text-(--text-muted)">{{ label }}</p>
            <div v-if="canResize" class="px-3 py-1.5 flex items-center justify-between gap-2 text-sm">
                <label :for="widthId" class="text-(--text-muted)">{{ t('stationPages.editor.width') }} %</label>
                <div class="w-20">
                    <BaseInput
                        :id="widthId"
                        type="number"
                        min="10"
                        max="100"
                        step="1"
                        :model-value="Math.round(widthPercent ?? 0)"
                        class="text-xs text-right"
                        @click.stop
                        @change="(e: Event) => commitWidth((e.target as HTMLInputElement).value)"
                    />
                </div>
            </div>
            <div class="border-t border-(--border) my-1"/>
            <DropdownMenuItem :icon="['fas', 'copy']" @click="emit('copy'); close()">{{ t('stationPages.editor.copyCell') }}</DropdownMenuItem>
            <DropdownMenuItem :icon="['fas', 'scissors']" @click="emit('cut'); close()">{{ t('stationPages.editor.cutCell') }}</DropdownMenuItem>
            <DropdownMenuItem v-if="canPaste" :icon="['fas', 'paste']" @click="emit('paste'); close()">{{ t('stationPages.editor.pasteCell') }}</DropdownMenuItem>
            <DropdownMenuItem :icon="['fas', 'table-columns']" @click="doSplit(2)">{{ t('stationPages.editor.splitCell') }}</DropdownMenuItem>
            <DropdownMenuItem v-if="restrictable" :icon="['fas', 'eye']" data-testid="cell-visibility-open"
                              @click="emit('visibility'); close()">{{ t('stationPages.editor.visibility') }}</DropdownMenuItem>
            <div class="border-t border-(--border) my-1"/>
            <DropdownMenuItem :icon="['fas', 'trash']" class="text-error" @click="emit('delete'); close()">{{ t('common.delete') }}</DropdownMenuItem>
        </div>
    </div>
</template>
