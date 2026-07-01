/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PageEditorHeader from './pageeditorview/PageEditorHeader.vue'
import MetadataPanel from './pageeditorview/MetadataPanel.vue'
import PageEditorAddRowDivider from './pageeditorview/PageEditorAddRowDivider.vue'
import RowsList from './pageeditorview/RowsList.vue'
import AddRowDialog from './pageeditorview/AddRowDialog.vue'
import type {RowEditData} from './pageeditorview/EditorRow.vue'
import type {CellEditData} from './pageeditorview/EditorCell.vue'
import {
    getPage,
    savePage,
    listPages,
    CellContentType,
    type StationPage,
    type SavePageRequest,
    type SaveRowRequest,
    type SaveCellRequest,
} from '@/api/pageManage'
import {useSession} from '@/composables/useSession'
import {useToast} from '@/composables/useToast'
import {usePageClipboard} from '@/composables/usePageClipboard'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {sessionInfo} = useSession()
const {show: showToast} = useToast()
const {pasteRow, hasClipboard, clipboardType} = usePageClipboard()

const page = ref<StationPage | null>(null)
const allPages = ref<StationPage[]>([])
const preview = ref(false)
const hasUnsavedChanges = ref(false)

// Editable fields
const title = ref('')
const slug = ref('')
const parentId = ref<number | null>(null)
const metaDescription = ref('')
const rows = ref<RowEditData[]>([])

const stationUid = computed(() => sessionInfo.value?.stationId ?? '')
const pageId = computed(() => Number(route.params.id))

const parentOptions = computed(() =>
    allPages.value.filter(p => p.id !== pageId.value),
)

function pageToRows(p: StationPage): RowEditData[] {
    return p.rows
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map(r => ({
            id: r.id,
            sortOrder: r.sortOrder,
            cells: r.cells
                .sort((a, b) => a.sortOrder - b.sortOrder)
                .map(c => ({
                    id: c.id,
                    sortOrder: c.sortOrder,
                    widthPercent: c.widthPercent,
                    contentType: c.contentType,
                    content: c.content,
                    config: c.config as Record<string, unknown>,
                })),
        }))
}

const {loading, error} = useAsyncLoader(async () => {
    const [p, pages] = await Promise.all([
        getPage(pageId.value),
        listPages(),
    ])
    page.value = p
    allPages.value = pages.pages
    title.value = p.title
    slug.value = p.slug
    parentId.value = p.parentId
    metaDescription.value = p.metaDescription ?? ''
    rows.value = pageToRows(p)
    hasUnsavedChanges.value = false
})

function markDirty() {
    hasUnsavedChanges.value = true
}

watch([title, slug, parentId, metaDescription], () => markDirty())

function updateRow(index: number, row: RowEditData) {
    rows.value[index] = row
    markDirty()
}

function deleteRow(index: number) {
    rows.value.splice(index, 1)
    markDirty()
}

function moveRow(index: number, direction: number) {
    const target = index + direction
    if (target < 0 || target >= rows.value.length) return
    const items = [...rows.value]
    const [moved] = items.splice(index, 1)
    items.splice(target, 0, moved)
    rows.value = items
    markDirty()
}

// Add-row dialog: pick column count first, then create the row with that many empty cells.
const addRowAt = ref<number | null>(null)
const showAddRowDialog = ref(false)

function openAddRowDialog(atIndex?: number) {
    addRowAt.value = atIndex ?? null
    showAddRowDialog.value = true
}

function addRow(columns: number) {
    const widthPercent = 100 / columns
    const newRow: RowEditData = {
        id: 0,
        sortOrder: rows.value.length,
        cells: Array.from({length: columns}, (_, i) => ({
            id: 0,
            sortOrder: i,
            widthPercent,
            contentType: CellContentType.EMPTY,
            content: '',
            config: {},
        })),
    }
    if (addRowAt.value != null) {
        rows.value.splice(addRowAt.value, 0, newRow)
    } else {
        rows.value.push(newRow)
    }
    showAddRowDialog.value = false
    addRowAt.value = null
    markDirty()
}

function onPasteRow(atIndex?: number) {
    const data = pasteRow() as RowEditData | null
    if (!data) return
    const newRow: RowEditData = {
        ...data,
        id: 0,
        sortOrder: rows.value.length,
        cells: data.cells.map(c => ({...c, id: 0})),
    }
    if (atIndex != null) {
        rows.value.splice(atIndex, 0, newRow)
    } else {
        rows.value.push(newRow)
    }
    markDirty()
}

function togglePreview() {
    preview.value = !preview.value
}

async function save() {
    error.value = ''
    try {
        const saveRows: SaveRowRequest[] = rows.value.map((r, ri) => ({
            sortOrder: ri,
            cells: r.cells.map((c, ci): SaveCellRequest => ({
                sortOrder: ci,
                widthPercent: c.widthPercent,
                contentType: c.contentType,
                content: c.content,
                config: c.config,
            })),
        }))
        const request: SavePageRequest = {
            title: title.value,
            slug: slug.value,
            parentId: parentId.value,
            metaDescription: metaDescription.value || null,
            ogImageId: page.value?.ogImageId ?? null,
            rows: saveRows,
        }
        const updated = await savePage(pageId.value, request)
        page.value = updated
        rows.value = pageToRows(updated)
        hasUnsavedChanges.value = false
        showToast(t('common.saved'), 'success')
    } catch (e) {
        error.value = t('common.error')
        throw e
    }
}

</script>

<template>
    <ViewContent :title="t('pages.page-editor.title')" :subtitle="t('pages.page-editor.subtitle')">
        <div class="space-y-4">
            <Spinner v-if="loading" size="lg"/>

            <template v-if="!loading && page">
                <PageEditorHeader
                    :title="page.title || t('stationPages.editor.newPage')"
                    :has-unsaved-changes="hasUnsavedChanges"
                    :preview="preview"
                    :save="save"
                    @back="router.push({name: 'pages-list'})"
                    @toggle-preview="togglePreview"
                />

                <Alert v-if="error" variant="error">{{ error }}</Alert>

                <MetadataPanel
                    v-if="!preview"
                    v-model:title="title"
                    v-model:slug="slug"
                    v-model:parent-id="parentId"
                    v-model:meta-description="metaDescription"
                    :parent-options="parentOptions"
                />

                <PageEditorAddRowDivider
                    v-if="!preview"
                    :has-clipboard="hasClipboard"
                    :clipboard-type="clipboardType"
                    @add="openAddRowDialog(0)"
                    @paste="onPasteRow(0)"
                />

                <RowsList
                    :rows="rows"
                    :page-id="pageId"
                    :station-uid="stationUid"
                    :preview="preview"
                    :has-clipboard="hasClipboard"
                    :clipboard-type="clipboardType"
                    @update:row="updateRow"
                    @delete="deleteRow"
                    @move-up="(i: number) => moveRow(i, -1)"
                    @move-down="(i: number) => moveRow(i, 1)"
                    @add-at="(i: number) => openAddRowDialog(i)"
                    @paste-at="(i: number) => onPasteRow(i)"
                />
            </template>
        </div>

        <AddRowDialog v-model="showAddRowDialog" @select="addRow"/>
    </ViewContent>
</template>
