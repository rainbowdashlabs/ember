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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import PageEditorHeader from './pageeditorview/PageEditorHeader.vue'
import MetadataPanel from './pageeditorview/MetadataPanel.vue'
import ContentBlockEditor from '@/components/content/ContentBlockEditor.vue'
import type {RowEditData} from '@/components/content/blockeditor/EditorRow.vue'
import {
    getPage,
    savePage,
    listPages,
    type StationPage,
    type SavePageRequest,
    type SaveRowRequest,
    type SaveCellRequest,
} from '@/api/pageManage'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure} from '@/util/failure'
import {showToast} from '@/util/toast'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {sessionInfo} = useSession()

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

/** Held so that a save can tell the editor its draft is no longer a rescue worth keeping. */
const editor = ref<{draftSaved: () => void} | null>(null)

/**
 * The page's own title at the head of the editor, because "Seite bearbeiten" stands above every one
 * of them and it is what the tab, the history and a bookmark carry. The stored title is what is
 * shown rather than the field being typed into, so the header does not change under the reader's
 * hands, and the wording holds the place while the page loads.
 */
const pageTitle = computed(() => page.value?.title || t('pages.page-editor.title'))

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

const {loading, failure} = useAsyncLoader(async () => {
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

function togglePreview() {
    preview.value = !preview.value
}

async function save() {
    failure.value = null
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
        editor.value?.draftSaved()
        showToast(t('common.saved'), 'success')
    } catch (e) {
        failure.value = {...describeFailure(e, t), message: t('stationPages.saveFailed')}
        throw e
    }
}

</script>

<template>
    <ViewContent :title="pageTitle" :subtitle="t('pages.page-editor.subtitle')">
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

                <FailureAlert :failure="failure"/>

                <MetadataPanel
                    v-if="!preview"
                    v-model:title="title"
                    v-model:slug="slug"
                    v-model:parent-id="parentId"
                    v-model:meta-description="metaDescription"
                    :parent-options="parentOptions"
                />

                <ContentBlockEditor
                    ref="editor"
                    v-model:rows="rows"
                    :station-uid="stationUid"
                    :preview="preview"
                    :draft-key="`page:${stationUid}:${pageId}`"
                    @change="markDirty"
                />
            </template>
        </div>
    </ViewContent>
</template>
