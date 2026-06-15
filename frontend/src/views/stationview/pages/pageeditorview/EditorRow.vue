/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EditorCell from './EditorCell.vue'
import ColumnResizeHandle from './ColumnResizeHandle.vue'
import type {CellEditData} from './EditorCell.vue'
import {CellContentType} from '@/api/pageManage'
import {usePageClipboard} from '@/composables/usePageClipboard'

export interface RowEditData {
    id: number
    sortOrder: number
    cells: CellEditData[]
}

const props = defineProps<{
    row: RowEditData
    pageId: number
    stationUid: string
    preview: boolean
    isFirst: boolean
    isLast: boolean
}>()

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
    // Single-column row → fall back to deleting the whole row.
    if (props.row.cells.length <= 1) {
        emit('delete')
        return
    }
    const current = props.row.cells[index]
    // Multi-column row, cell already empty → drop the column and redistribute widths.
    if (current.contentType === CellContentType.EMPTY) {
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
    // Multi-column row with content → reset the cell to an empty container so the column count
    // and widths are preserved and the user can pick a new content type for that slot.
    const cells = [...props.row.cells]
    cells[index] = {
        ...cells[index],
        contentType: CellContentType.EMPTY,
        content: '',
        config: {},
    }
    updateCells(cells)
}

function splitColumns(count: number) {
    const widthPercent = 100 / count
    const cells: CellEditData[] = []
    for (let i = 0; i < count; i++) {
        if (i < props.row.cells.length) {
            cells.push({...props.row.cells[i], widthPercent, sortOrder: i})
        } else {
            cells.push({
                id: 0,
                sortOrder: i,
                widthPercent,
                contentType: CellContentType.EMPTY,
                content: '',
                config: {},
            })
        }
    }
    updateCells(cells)
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
    <!-- Preview mode -->
    <div v-if="preview" class="flex gap-2">
        <div
            v-for="(cell, ci) in row.cells"
            :key="ci"
            :style="{width: `${cell.widthPercent}%`}"
            class="min-w-0"
        >
            <EditorCell
                :cell="cell"
                :page-id="pageId"
                :station-uid="stationUid"
                :preview="true"
                @update:cell="updateCell(ci, $event)"
                @delete="deleteCell(ci)"
            />
        </div>
    </div>

    <!-- Edit mode -->
    <NeutralContainer v-else class="space-y-2">
        <!-- Row toolbar -->
        <div class="flex items-center justify-between gap-2">
            <div class="flex items-center gap-1">
                <!-- Column split buttons -->
                <IconButton
                    v-for="n in 4" :key="n"
                    :icon="['fas', 'square']"
                    :label="t(`stationPages.editor.${['oneColumn','twoColumns','threeColumns','fourColumns'][n-1]}`)"
                    @click="splitColumns(n)"
                >
                    <span class="inline-flex gap-0.5 h-4 w-5">
                        <span v-for="i in n" :key="i"
                              class="flex-1 min-w-0.5 self-stretch rounded-sm"
                              :class="row.cells.length === n ? 'bg-primary' : 'bg-[var(--text-muted)]'"
                        />
                    </span>
                </IconButton>
            </div>

            <div class="flex items-center gap-1">
                <IconButton
                    :icon="['fas', 'angle-up']"
                    :label="t('common.moveUp')"
                    class="text-[var(--text-muted)] hover:text-[var(--text)]"
                    :class="{'opacity-30 pointer-events-none': isFirst}"
                    @click="$emit('move-up')"
                />
                <IconButton
                    :icon="['fas', 'angle-down']"
                    :label="t('common.moveDown')"
                    class="text-[var(--text-muted)] hover:text-[var(--text)]"
                    :class="{'opacity-30 pointer-events-none': isLast}"
                    @click="$emit('move-down')"
                />
                <IconButton
                    :icon="['fas', 'copy']"
                    :label="t('stationPages.editor.copyRow')"
                    class="text-[var(--text-muted)] hover:text-[var(--text)]"
                    @click="onCopy"
                />
                <IconButton
                    :icon="['fas', 'scissors']"
                    :label="t('stationPages.editor.cutRow')"
                    class="text-[var(--text-muted)] hover:text-[var(--text)]"
                    @click="onCut"
                />
                <IconButton
                    v-if="hasClipboard && clipboardType === 'cell'"
                    :icon="['fas', 'paste']"
                    :label="t('stationPages.editor.pasteCell')"
                    class="text-primary hover:text-primary-accent"
                    @click="onPasteCell"
                />
                <DeleteButton @click="$emit('delete')"/>
            </div>
        </div>

        <!-- Cells -->
        <div class="editor-row-cells flex items-stretch gap-0">
            <template v-for="(cell, ci) in row.cells" :key="ci">
                <div :style="{width: `${cell.widthPercent}%`}" class="min-w-0 flex flex-col">
                    <EditorCell
                        :cell="cell"
                        :page-id="pageId"
                        :station-uid="stationUid"
                        :preview="false"
                        :can-resize="row.cells.length > 1"
                        @update:cell="updateCell(ci, $event)"
                        @update:width="setCellWidth(ci, $event)"
                        @delete="deleteCell(ci)"
                    />
                </div>
                <div v-if="ci < row.cells.length - 1" class="relative flex items-center justify-center">
                    <ColumnResizeHandle
                        :left-percent="cell.widthPercent"
                        :right-percent="row.cells[ci + 1].widthPercent"
                        @resize="onResize(ci, $event)"
                    />
                    <IconButton
                        :icon="['fas', 'arrow-right-arrow-left']"
                        :label="t('stationPages.editor.swapCells')"
                        class="absolute top-1 left-1/2 -translate-x-1/2 text-[var(--text-muted)] hover:text-[var(--text)] text-xs bg-(--bg) rounded-full"
                        @click="swapCells(ci)"
                    />
                </div>
            </template>
        </div>
    </NeutralContainer>
</template>
