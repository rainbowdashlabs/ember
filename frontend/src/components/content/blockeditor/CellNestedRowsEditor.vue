/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import EditorFloatButton from './EditorFloatButton.vue'
import EditorRow, {type RowEditData} from './EditorRow.vue'
import {CellContentType} from '@/api/pageManage'

/**
 * Edit-mode renderer for a NESTED_ROWS cell. Renders each nested row with insert / move handles
 * between them; emits the full updated rows list whenever anything changes.
 */
const rows = defineModel<RowEditData[]>('rows', {required: true})

defineProps<{
    stationUid: string
    depth: number
}>()

const {t} = useI18n()

function updateRow(index: number, row: RowEditData) {
    const next = [...rows.value]
    next[index] = row
    rows.value = next
}

function deleteRow(index: number) {
    rows.value = rows.value.filter((_, i) => i !== index)
}

function moveRow(index: number, delta: number) {
    const next = [...rows.value]
    const target = index + delta
    if (target < 0 || target >= next.length) return
    const moved = next.splice(index, 1)[0]
    if (!moved) return
    next.splice(target, 0, moved)
    rows.value = next
}

function insertRowAt(index: number) {
    const row: RowEditData = {
        id: 0,
        sortOrder: index,
        cells: [{id: 0, sortOrder: 0, widthPercent: 100, contentType: CellContentType.EMPTY, content: '', config: {}}],
    }
    const next = [...rows.value]
    next.splice(index, 0, row)
    rows.value = next
}
</script>

<template>
    <div class="border-l-2 border-primary/30 pl-2">
        <div class="relative flex items-center justify-center py-2">
            <div class="absolute inset-x-0 top-1/2 h-px bg-(--border)"/>
            <EditorFloatButton :icon="['fas', 'plus']" :label="t('stationPages.editor.addComponent')"
                class="relative"
                @click="insertRowAt(0)"/>
        </div>
        <template v-for="(row, ri) in rows" :key="row.id + '-' + ri">
            <EditorRow :row="row" :station-uid="stationUid" :preview="false"
                :is-first="ri === 0" :is-last="ri === rows.length - 1" :depth="depth + 1"
                @update:row="updateRow(ri, $event)" @delete="deleteRow(ri)"
                @move-up="moveRow(ri, -1)" @move-down="moveRow(ri, 1)"/>
            <div class="relative flex items-center justify-center gap-2 py-2">
                <div class="absolute inset-x-0 top-1/2 h-px bg-(--border)"/>
                <EditorFloatButton :icon="['fas', 'plus']" :label="t('stationPages.editor.addComponent')"
                    class="relative"
                    @click="insertRowAt(ri + 1)"/>
                <EditorFloatButton v-if="ri < rows.length - 1"
                    :icon="['fas', 'arrows-up-down']" :label="t('stationPages.editor.swapComponents')"
                    class="relative"
                    @click="moveRow(ri, 1)"/>
            </div>
        </template>
    </div>
</template>
