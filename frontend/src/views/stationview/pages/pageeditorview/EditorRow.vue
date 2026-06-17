/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EditorCell from './EditorCell.vue'
import ColumnGutter from './ColumnGutter.vue'
import RowActionsMenu from './RowActionsMenu.vue'
import PublicPageRow from '@/views/public/publicpageview/PublicPageRow.vue'
import type {CellEditData} from './EditorCell.vue'
import {CellContentType, type PageRow} from '@/api/pageManage'
import {usePageClipboard} from '@/composables/usePageClipboard'

export interface RowEditData {
    id: number
    sortOrder: number
    cells: CellEditData[]
}

const props = withDefaults(defineProps<{
    row: RowEditData
    pageId: number
    stationUid: string
    preview: boolean
    isFirst: boolean
    isLast: boolean
    depth?: number
}>(), {
    depth: 0,
})

const emit = defineEmits<{
    'update:row': [row: RowEditData]
    delete: []
    'move-up': []
    'move-down': []
}>()

const {t} = useI18n()
const {copyRow, cutRow, pasteCell, hasClipboard, clipboardType} = usePageClipboard()

function updateCells(cells: CellEditData[]) {
    emit('update:row', {...props.row, cells})
}

function updateCell(index: number, cell: CellEditData) {
    const cells = [...props.row.cells]
    cells[index] = cell
    updateCells(cells)
}

function deleteCell(index: number) {
    const current = props.row.cells[index]
    const isEmpty = current.contentType === CellContentType.EMPTY
    if (isEmpty && props.row.cells.length <= 1) {
        emit('delete')
        return
    }
    if (isEmpty) {
        const remaining = props.row.cells.filter((_, i) => i !== index)
        const total = remaining.reduce((sum, c) => sum + c.widthPercent, 0)
        const redistributed = remaining.map((c, i) => ({
            ...c,
            widthPercent: total === 0 ? 100 / remaining.length : (c.widthPercent / total) * 100,
            sortOrder: i,
        }))
        updateCells(redistributed)
        return
    }
    if (props.row.cells.length <= 1) {
        const cells = [...props.row.cells]
        cells[index] = {...current, contentType: CellContentType.EMPTY, content: '', config: {}}
        updateCells(cells)
        return
    }
    const cells = [...props.row.cells]
    cells[index] = {...cells[index], contentType: CellContentType.EMPTY, content: '', config: {}}
    updateCells(cells)
}

function insertColumn(index: number) {
    if (props.row.cells.length >= 4) return
    const count = props.row.cells.length + 1
    const widthPercent = 100 / count
    const newCell: CellEditData = {
        id: 0,
        sortOrder: index,
        widthPercent,
        contentType: CellContentType.EMPTY,
        content: '',
        config: {},
    }
    const next = [...props.row.cells]
    next.splice(index, 0, newCell)
    updateCells(next.map((c, i) => ({...c, widthPercent, sortOrder: i})))
}

function onResize(cellIndex: number, leftDelta: number) {
    const cells = [...props.row.cells]
    cells[cellIndex] = {...cells[cellIndex], widthPercent: cells[cellIndex].widthPercent + leftDelta}
    cells[cellIndex + 1] = {...cells[cellIndex + 1], widthPercent: cells[cellIndex + 1].widthPercent - leftDelta}
    updateCells(cells)
}

function setCellWidth(cellIndex: number, widthPercent: number) {
    if (props.row.cells.length < 2) return
    const clamped = Math.max(10, Math.min(100 - 10 * (props.row.cells.length - 1), widthPercent))
    const remainingTotal = 100 - clamped
    const otherCells = props.row.cells.filter((_, i) => i !== cellIndex)
    const otherTotal = otherCells.reduce((sum, c) => sum + c.widthPercent, 0)
    const cells = props.row.cells.map((c, i) => {
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
    const cells = [...props.row.cells]
    const temp = cells[leftIndex]
    cells[leftIndex] = cells[leftIndex + 1]
    cells[leftIndex + 1] = temp
    updateCells(cells)
}

function onCopy() {
    copyRow(props.row)
}

function onCut() {
    cutRow(props.row, () => emit('delete'))
}

function onPasteCell() {
    const data = pasteCell() as CellEditData | null
    if (!data) return
    const cells = [...props.row.cells]
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
    <PublicPageRow
        v-if="preview"
        :row="(row as unknown as PageRow)"
        :station-uid="stationUid"
        :page-title="''"
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
                        :page-id="pageId"
                        :station-uid="stationUid"
                        :preview="false"
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
                    :right-percent="row.cells[ci + 1].widthPercent"
                    :can-add-column="row.cells.length < 4"
                    @resize="onResize(ci, $event)"
                    @swap="swapCells(ci)"
                    @add-column="insertColumn(ci + 1)"
                />
            </template>
            <IconButton
                v-if="row.cells.length < 4"
                :icon="['fas', 'plus']"
                :label="t('stationPages.editor.addColumn')"
                class="absolute -left-2 top-1/2 -translate-y-1/2 z-10 rounded-full bg-(--bg) border border-(--border) text-(--text-muted) hover:text-primary hover:border-primary !p-1 text-xs shadow-sm"
                @click="insertColumn(0)"
            />
            <IconButton
                v-if="row.cells.length < 4"
                :icon="['fas', 'plus']"
                :label="t('stationPages.editor.addColumn')"
                class="absolute -right-2 top-1/2 -translate-y-1/2 z-10 rounded-full bg-(--bg) border border-(--border) text-(--text-muted) hover:text-primary hover:border-primary !p-1 text-xs shadow-sm"
                @click="insertColumn(row.cells.length)"
            />
        </div>
    </NeutralContainer>
</template>
