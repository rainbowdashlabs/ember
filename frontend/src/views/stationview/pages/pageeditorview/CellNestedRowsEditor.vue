/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import EditorRow, {type RowEditData} from './EditorRow.vue'
import {CellContentType} from '@/api/pageManage'

/**
 * Edit-mode renderer for a NESTED_ROWS cell. Renders each nested row with insert / move handles
 * between them; emits the full updated rows list whenever anything changes.
 */
const props = defineProps<{
    rows: RowEditData[]
    pageId: number
    stationUid: string
    depth: number
}>()

const emit = defineEmits<{
    'update:rows': [rows: RowEditData[]]
}>()

const {t} = useI18n()

function updateRow(index: number, row: RowEditData) {
    const next = [...props.rows]
    next[index] = row
    emit('update:rows', next)
}

function deleteRow(index: number) {
    emit('update:rows', props.rows.filter((_, i) => i !== index))
}

function moveRow(index: number, delta: number) {
    const next = [...props.rows]
    const target = index + delta
    if (target < 0 || target >= next.length) return
    const moved = next.splice(index, 1)[0]
    if (!moved) return
    next.splice(target, 0, moved)
    emit('update:rows', next)
}

function insertRowAt(index: number) {
    const row: RowEditData = {
        id: 0,
        sortOrder: index,
        cells: [{id: 0, sortOrder: 0, widthPercent: 100, contentType: CellContentType.EMPTY, content: '', config: {}}],
    }
    const next = [...props.rows]
    next.splice(index, 0, row)
    emit('update:rows', next)
}
</script>

<template>
    <div class="border-l-2 border-primary/30 pl-2">
        <div class="relative flex items-center justify-center py-2">
            <div class="absolute inset-x-0 top-1/2 h-px bg-(--border)"/>
            <IconButton :icon="['fas', 'plus']" :label="t('stationPages.editor.addComponent')"
                class="relative rounded-full bg-(--bg) border border-(--border) text-(--text-muted) hover:text-primary hover:border-primary !p-1 text-xs shadow-sm"
                @click="insertRowAt(0)"/>
        </div>
        <template v-for="(row, ri) in rows" :key="row.id + '-' + ri">
            <EditorRow :row="row" :page-id="pageId" :station-uid="stationUid" :preview="false"
                :is-first="ri === 0" :is-last="ri === rows.length - 1" :depth="depth + 1"
                @update:row="updateRow(ri, $event)" @delete="deleteRow(ri)"
                @move-up="moveRow(ri, -1)" @move-down="moveRow(ri, 1)"/>
            <div class="relative flex items-center justify-center gap-2 py-2">
                <div class="absolute inset-x-0 top-1/2 h-px bg-(--border)"/>
                <IconButton :icon="['fas', 'plus']" :label="t('stationPages.editor.addComponent')"
                    class="relative rounded-full bg-(--bg) border border-(--border) text-(--text-muted) hover:text-primary hover:border-primary !p-1 text-xs shadow-sm"
                    @click="insertRowAt(ri + 1)"/>
                <IconButton v-if="ri < rows.length - 1"
                    :icon="['fas', 'arrows-up-down']" :label="t('stationPages.editor.swapComponents')"
                    class="relative rounded-full bg-(--bg) border border-(--border) text-(--text-muted) hover:text-primary hover:border-primary !p-1 text-xs shadow-sm"
                    @click="moveRow(ri, 1)"/>
            </div>
        </template>
    </div>
</template>
