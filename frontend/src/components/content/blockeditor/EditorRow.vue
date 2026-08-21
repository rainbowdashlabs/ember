/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EditorCell from './EditorCell.vue'
import EditorFloatButton from './EditorFloatButton.vue'
import ColumnGutter from './ColumnGutter.vue'
import RowActionsMenu from './RowActionsMenu.vue'
import ContentRow from '@/components/content/ContentRow.vue'
import {publicContentContext} from '@/util/contentContext'
import type {CellEditData} from './EditorCell.vue'
import {CellContentType, type PageRow} from '@/api/pageManage'
import {usePageClipboard} from '@/composables/usePageClipboard'

export interface RowEditData {
    id: number
    sortOrder: number
    cells: CellEditData[]
}

const row = defineModel<RowEditData>('row', {required: true})

const props = withDefaults(defineProps<{
    stationUid: string
    preview: boolean
    isFirst: boolean
    isLast: boolean
    depth?: number
}>(), {
    depth: 0,
})

const emit = defineEmits<{
    delete: []
    'move-up': []
    'move-down': []
}>()

const {t} = useI18n()
const {copyRow, cutRow, pasteCell, hasClipboard, clipboardType} = usePageClipboard()

function updateCells(cells: CellEditData[]) {
    row.value = {...row.value, cells}
}

function updateCell(index: number, cell: CellEditData) {
    const cells = [...row.value.cells]
    cells[index] = cell
    updateCells(cells)
}

function deleteCell(index: number) {
    const current = row.value.cells[index]
    if (!current) return
    const isEmpty = current.contentType === CellContentType.EMPTY
    if (isEmpty && row.value.cells.length <= 1) {
        emit('delete')
        return
    }
    if (isEmpty) {
        const remaining = row.value.cells.filter((_, i) => i !== index)
        const total = remaining.reduce((sum, c) => sum + c.widthPercent, 0)
        const redistributed = remaining.map((c, i) => ({
            ...c,
            widthPercent: total === 0 ? 100 / remaining.length : (c.widthPercent / total) * 100,
            sortOrder: i,
        }))
        updateCells(redistributed)
        return
    }
    if (row.value.cells.length <= 1) {
        const cells = [...row.value.cells]
        cells[index] = {...current, contentType: CellContentType.EMPTY, content: '', config: {}}
        updateCells(cells)
        return
    }
    const cells = [...row.value.cells]
    cells[index] = {...current, contentType: CellContentType.EMPTY, content: '', config: {}}
    updateCells(cells)
}

function insertColumn(index: number) {
    if (row.value.cells.length >= 4) return
    const count = row.value.cells.length + 1
    const widthPercent = 100 / count
    const newCell: CellEditData = {
        id: 0,
        sortOrder: index,
        widthPercent,
        contentType: CellContentType.EMPTY,
        content: '',
        config: {},
    }
    const next = [...row.value.cells]
    next.splice(index, 0, newCell)
    updateCells(next.map((c, i) => ({...c, widthPercent, sortOrder: i})))
}

function onResize(cellIndex: number, leftDelta: number) {
    const cells = [...row.value.cells]
    const left = cells[cellIndex]
    const right = cells[cellIndex + 1]
    if (!left || !right) return
    cells[cellIndex] = {...left, widthPercent: left.widthPercent + leftDelta}
    cells[cellIndex + 1] = {...right, widthPercent: right.widthPercent - leftDelta}
    updateCells(cells)
}

function setCellWidth(cellIndex: number, widthPercent: number) {
    if (row.value.cells.length < 2) return
    const clamped = Math.max(10, Math.min(100 - 10 * (row.value.cells.length - 1), widthPercent))
    const remainingTotal = 100 - clamped
    const otherCells = row.value.cells.filter((_, i) => i !== cellIndex)
    const otherTotal = otherCells.reduce((sum, c) => sum + c.widthPercent, 0)
    const cells = row.value.cells.map((c, i) => {
        if (i === cellIndex) return {...c, widthPercent: clamped}
        // Scale the rest proportionally so the row still sums to 100.
        const newWidth = otherTotal === 0
            ? remainingTotal / otherCells.length
            : (c.widthPercent / otherTotal) * remainingTotal
        return {...c, widthPercent: Math.max(10, newWidth)}
    })
    updateCells(cells)
}

function swapCells(leftIndex: number) {
    const cells = [...row.value.cells]
    const left = cells[leftIndex]
    const right = cells[leftIndex + 1]
    if (!left || !right) return
    cells[leftIndex] = right
    cells[leftIndex + 1] = left
    updateCells(cells)
}

function onCopy() {
    copyRow(row.value)
}

function onCut() {
    cutRow(row.value, () => emit('delete'))
}

function onPasteCell() {
    const data = pasteCell() as CellEditData | null
    if (!data) return
    const cells = [...row.value.cells]
    const newCell: CellEditData = {
        ...data,
        id: 0,
        sortOrder: cells.length,
    }
    // Redistribute widths
    const count = cells.length + 1
    const widthPercent = 100 / count
    const adjusted = [...cells, newCell].map((c, i) => ({...c, widthPercent, sortOrder: i}))
    updateCells(adjusted)
}
</script>

<template>
    <ContentRow
        v-if="preview"
        :row="(row as unknown as PageRow)"
        :context="publicContentContext(stationUid)"
    />

    <!-- Edit mode -->
    <NeutralContainer
        v-else
        :padded="row.cells.length > 1"
        class="group relative space-y-2"
        :class="row.cells.length === 1 ? '!border-transparent !bg-transparent' : ''"
    >
        <RowActionsMenu
            v-if="row.cells.length > 1"
            :is-first="isFirst"
            :is-last="isLast"
            :can-paste-cell="hasClipboard && clipboardType === 'cell'"
            @copy="onCopy"
            @cut="onCut"
            @delete="$emit('delete')"
            @move-up="$emit('move-up')"
            @move-down="$emit('move-down')"
            @paste-cell="onPasteCell"
        />

        <!-- Cells -->
        <div class="editor-row-cells relative flex items-stretch gap-0">
            <template v-for="(cell, ci) in row.cells" :key="ci">
                <div :style="{width: `${cell.widthPercent}%`}" class="min-w-0 flex flex-col">
                    <EditorCell
                        :cell="cell"
                        :station-uid="stationUid"
                        :can-resize="row.cells.length > 1"
                        :depth="depth"
                        @update:cell="updateCell(ci, $event)"
                        @update:width="setCellWidth(ci, $event)"
                        @delete="deleteCell(ci)"
                    />
                </div>
                <ColumnGutter
                    v-if="ci < row.cells.length - 1"
                    :left-percent="cell.widthPercent"
                    :right-percent="row.cells[ci + 1]?.widthPercent ?? 0"
                    :can-add-column="row.cells.length < 4"
                    @resize="onResize(ci, $event)"
                    @swap="swapCells(ci)"
                    @add-column="insertColumn(ci + 1)"
                />
            </template>
            <EditorFloatButton
                v-if="row.cells.length < 4"
                :icon="['fas', 'plus']"
                :label="t('stationPages.editor.addColumn')"
                class="absolute -left-2 top-1/2 -translate-y-1/2 z-10"
                @click="insertColumn(0)"
            />
            <EditorFloatButton
                v-if="row.cells.length < 4"
                :icon="['fas', 'plus']"
                :label="t('stationPages.editor.addColumn')"
                class="absolute -right-2 top-1/2 -translate-y-1/2 z-10"
                @click="insertColumn(row.cells.length)"
            />
        </div>
    </NeutralContainer>
</template>
