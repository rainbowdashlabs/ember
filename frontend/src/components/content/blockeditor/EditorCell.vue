/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CellImageEditor from './CellImageEditor.vue'
import EditorFloatButton from './EditorFloatButton.vue'
import CellVideoEditor from './CellVideoEditor.vue'
import CellLayoutEditors from './CellLayoutEditors.vue'
import CellLayoutRender from './CellLayoutRender.vue'
import CellActionsMenu from './CellActionsMenu.vue'
import CellEmptyChooser from './CellEmptyChooser.vue'
import CellMarkdownInline from './CellMarkdownInline.vue'
import CellNestedRowsEditor from './CellNestedRowsEditor.vue'
import CellRestrictionChips from './CellRestrictionChips.vue'
import CellVisibilityDialog from './CellVisibilityDialog.vue'
import type {RowEditData} from './EditorRow.vue'
import {isLayoutKind, type LayoutKindName} from '@/api/pageManage'
import {CellContentType} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'
import {usePageClipboard} from '@/composables/usePageClipboard'
import {useBlockEditorOptions} from '@/composables/useBlockEditorOptions'

/**
 * Data shape passed between the page editor and a single cell. {@code id} is 0 for cells that
 * exist only in the draft and have not yet been persisted. {@code restriction} says who the block is
 * shown to, and is only ever set where the editor offers a visibility, which a letter does.
 */
export interface CellEditData {
    id: number
    sortOrder: number
    widthPercent: number
    contentType: CellContentType
    content: string
    config: Record<string, unknown>
    restriction?: RestrictionSelection | null
}

const cell = defineModel<CellEditData>('cell', {required: true})

const props = defineProps<{
    stationUid: string
    canResize?: boolean
    /** Nesting depth (0 at top). Used to soft-warn at deep levels and shrink resize handles. */
    depth?: number
}>()

const emit = defineEmits<{
    'update:width': [widthPercent: number]
    delete: []
}>()

const {t} = useI18n()
const {copyCell, cutCell, pasteCell, hasClipboard, clipboardType} = usePageClipboard()
const options = useBlockEditorOptions()
const visibilityOpen = ref(false)

const canPaste = computed(() => hasClipboard.value && clipboardType.value === 'cell')

const depth = computed(() => props.depth ?? 0)
const showDepthWarning = computed(() => depth.value >= 4)
const showWrapHandles = computed(() =>
    depth.value === 0
    && props.canResize
    && cell.value.contentType !== CellContentType.EMPTY,
)

const nestedRows = computed<RowEditData[]>(() => {
    const raw = (cell.value.config as {rows?: RowEditData[]}).rows
    return Array.isArray(raw) ? raw : []
})

function updateField<K extends keyof CellEditData>(key: K, value: CellEditData[K]) {
    cell.value = {...cell.value, [key]: value}
}

function onContentTypeChange(type: string) {
    cell.value = {...cell.value, contentType: type as CellContentType, content: '', config: {}}
}

function onPasteHere() {
    const data = pasteCell() as CellEditData | null
    if (!data) return
    cell.value = {...cell.value, contentType: data.contentType, content: data.content, config: data.config}
}

function emptyChild(sortOrder: number, widthPercent: number): CellEditData {
    return {id: 0, sortOrder, widthPercent, contentType: CellContentType.EMPTY, content: '', config: {}}
}

/** The current block as the first child of a nested row, taking who it is shown to along. */
function selfAsChild(widthPercent: number): CellEditData {
    return {
        id: 0, sortOrder: 0, widthPercent,
        contentType: cell.value.contentType, content: cell.value.content, config: cell.value.config,
        restriction: cell.value.restriction,
    }
}

/** The current cell turned into a nested-rows cell holding the given rows, shown to everybody. */
function nestedAs(rows: RowEditData[]): CellEditData {
    return {...cell.value, contentType: CellContentType.NESTED_ROWS, content: '', config: {rows}, restriction: undefined}
}

/**
 * Convert the current cell into a NESTED_ROWS cell with a single row whose first child holds the
 * original content; N-1 empty cells fill the remaining columns.
 */
function splitCell(columns: number) {
    const widthPercent = 100 / columns
    const cells: CellEditData[] = [selfAsChild(widthPercent)]
    for (let i = 1; i < columns; i++) cells.push(emptyChild(i, widthPercent))
    cell.value = nestedAs([{id: 0, sortOrder: 0, cells}])
}

/**
 * Wrap the current cell into a NESTED_ROWS cell containing two single-cell rows - the original
 * content stays in one and an empty cell is added above or below it.
 */
function wrapAndAddSibling(position: 'above' | 'below') {
    const currentRow: RowEditData = {id: 0, sortOrder: 0, cells: [selfAsChild(100)]}
    const emptyRow: RowEditData = {id: 0, sortOrder: 1, cells: [emptyChild(0, 100)]}
    cell.value = nestedAs(position === 'above' ? [emptyRow, currentRow] : [currentRow, emptyRow])
}
</script>

<template>
    <NeutralContainer class="group relative w-full flex-1 flex flex-col">
        <CellActionsMenu
            :label="cell.contentType === 'EMPTY' ? undefined : t(`stationPages.contentType.${cell.contentType.toLowerCase()}`)"
            :width-percent="cell.widthPercent"
            :can-resize="canResize"
            :can-paste="canPaste"
            :restrictable="!!options.restrictable"
            @copy="copyCell(cell)"
            @cut="cutCell(cell, () => emit('delete'))"
            @paste="onPasteHere"
            @delete="emit('delete')"
            @split="splitCell"
            @visibility="visibilityOpen = true"
            @update:width-percent="emit('update:width', $event ?? 0)"
        />

        <template v-if="options.restrictable">
            <CellRestrictionChips :restriction="cell.restriction" :choices="options.restrictable"/>
            <CellVisibilityDialog v-model="visibilityOpen" :restriction="cell.restriction" :choices="options.restrictable"
                                  @update:restriction="updateField('restriction', $event)"/>
        </template>

        <p v-if="showDepthWarning" class="text-[10px] text-error italic mb-2">
            <font-awesome-icon :icon="['fas', 'triangle-exclamation']" class="mr-1"/>
            {{ t('stationPages.editor.depthWarning') }}
        </p>

        <EditorFloatButton
            v-if="showWrapHandles"
            :icon="['fas', 'plus']"
            :label="t('stationPages.editor.addComponent')"
            class="absolute -top-2 left-1/2 -translate-x-1/2 z-10"
            @click="wrapAndAddSibling('above')"
        />

        <CellEmptyChooser
            v-if="cell.contentType === CellContentType.EMPTY"
            @pick="onContentTypeChange"
            @paste-here="onPasteHere"
        />

        <CellMarkdownInline
            v-else-if="cell.contentType === CellContentType.MARKDOWN"
            :content="cell.content"
            @update:content="updateField('content', $event)"
        />

        <CellImageEditor
            v-else-if="cell.contentType === CellContentType.IMAGE"
            :content="cell.content"
            :config="cell.config"
            :station-uid="stationUid"
            @update:content="updateField('content', $event)"
            @update:config="updateField('config', $event)"
        />

        <CellVideoEditor
            v-else-if="cell.contentType === CellContentType.VIDEO"
            :content="cell.content"
            :config="cell.config"
            @update:content="updateField('content', $event)"
            @update:config="updateField('config', $event)"
        />

        <CellNestedRowsEditor
            v-else-if="cell.contentType === CellContentType.NESTED_ROWS"
            :rows="nestedRows"
            :station-uid="stationUid"
            :depth="depth"
            @update:rows="updateField('config', {...cell.config, rows: $event})"
        />

        <template v-else-if="isLayoutKind(cell.contentType)">
            <div class="rounded-theme border border-dashed border-(--border) p-3 bg-bg-light-accent/20 dark:bg-bg-dark-accent/10">
                <CellLayoutRender
                    :kind="cell.contentType as LayoutKindName"
                    :content="cell.content"
                    :config="cell.config"
                    :station-uid="stationUid"
                />
            </div>
            <CellLayoutEditors
                :kind="cell.contentType as LayoutKindName"
                :content="cell.content"
                :config="cell.config"
                :station-uid="stationUid"
                @update:content="updateField('content', $event)"
                @update:config="updateField('config', $event)"
            />
        </template>

        <EditorFloatButton
            v-if="showWrapHandles"
            :icon="['fas', 'plus']"
            :label="t('stationPages.editor.addComponent')"
            class="absolute -bottom-2 left-1/2 -translate-x-1/2 z-10"
            @click="wrapAndAddSibling('below')"
        />
    </NeutralContainer>
</template>
