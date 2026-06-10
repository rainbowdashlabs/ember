/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, onMounted, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EditorRow from './pageeditorview/EditorRow.vue'
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

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {sessionInfo} = useSession()
const {show: showToast} = useToast()
const {pasteRow, hasClipboard, clipboardType} = usePageClipboard()

const page = ref<StationPage | null>(null)
const allPages = ref<StationPage[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
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

async function loadData() {
    loading.value = true
    error.value = ''
    try {
        const [p, pages] = await Promise.all([
            getPage(pageId.value),
            listPages(),
        ])
        page.value = p
        allPages.value = pages
        title.value = p.title
        slug.value = p.slug
        parentId.value = p.parentId
        metaDescription.value = p.metaDescription ?? ''
        rows.value = pageToRows(p)
        hasUnsavedChanges.value = false
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
}

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

function addRow(atIndex?: number) {
    const newRow: RowEditData = {
        id: 0,
        sortOrder: rows.value.length,
        cells: [{
            id: 0,
            sortOrder: 0,
            widthPercent: 100,
            contentType: CellContentType.MARKDOWN,
            content: '',
            config: {},
        }],
    }
    if (atIndex != null) {
        rows.value.splice(atIndex, 0, newRow)
    } else {
        rows.value.push(newRow)
    }
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
    saving.value = true
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
    } catch {
        error.value = t('common.error')
    } finally {
        saving.value = false
    }
}

onMounted(() => loadData())
</script>

<template>
    <ViewContent>
        <div class="space-y-4">
            <Spinner v-if="loading" size="lg"/>

            <template v-if="!loading && page">
                <!-- Header -->
                <div class="flex items-center justify-between flex-wrap gap-2">
                    <div class="flex items-center gap-3">
                        <IconButton
                            :icon="['fas', 'arrow-left']"
                            :label="t('common.back')"
                            class="text-[var(--text-muted)] hover:text-[var(--text)]"
                            @click="router.push({name: 'pages-list'})"
                        />
                        <PageHeader>{{ page.title || t('stationPages.editor.newPage') }}</PageHeader>
                        <span
                            v-if="hasUnsavedChanges"
                            class="text-xs text-info-accent font-medium"
                        >
                            {{ t('stationPages.editor.unsavedChanges') }}
                        </span>
                    </div>
                    <div class="flex gap-2">
                        <SecondaryButton @click="togglePreview">
                            <font-awesome-icon :icon="['fas', preview ? 'pen' : 'eye']" class="mr-1"/>
                            {{ preview ? t('stationPages.editor.edit') : t('stationPages.editor.preview') }}
                        </SecondaryButton>
                        <PrimaryButton :disabled="saving" @click="save">
                            <font-awesome-icon :icon="['fas', 'floppy-disk']" class="mr-1"/>
                            {{ saving ? t('common.saving') : t('common.save') }}
                        </PrimaryButton>
                    </div>
                </div>

                <Alert v-if="error" variant="error">{{ error }}</Alert>

                <!-- Metadata section (hidden in preview) -->
                <NeutralContainer v-if="!preview" class="space-y-3">
                    <SectionHeader>{{ t('stationPages.editor.metadata') }}</SectionHeader>
                    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div>
                            <FormLabel>{{ t('stationPages.editor.titleLabel') }}</FormLabel>
                            <TextInput v-model="title" :placeholder="t('stationPages.editor.titlePlaceholder')"/>
                        </div>
                        <div>
                            <FormLabel>{{ t('stationPages.editor.slugLabel') }}</FormLabel>
                            <TextInput v-model="slug" :placeholder="t('stationPages.editor.slugPlaceholder')"/>
                        </div>
                        <div>
                            <FormLabel>{{ t('stationPages.editor.parent') }}</FormLabel>
                            <SelectInput v-model="parentId as unknown as string">
                                <option :value="null">{{ t('stationPages.editor.noParent') }}</option>
                                <option
                                    v-for="p in parentOptions"
                                    :key="p.id"
                                    :value="p.id"
                                >
                                    {{ p.title }}
                                </option>
                            </SelectInput>
                        </div>
                        <div>
                            <FormLabel>{{ t('stationPages.editor.metaDescription') }}</FormLabel>
                            <TextInput
                                v-model="metaDescription"
                                :placeholder="t('stationPages.editor.metaDescriptionPlaceholder')"
                            />
                        </div>
                    </div>
                </NeutralContainer>

                <!-- Add row before first -->
                <div v-if="!preview" class="flex items-center justify-center gap-2 py-1">
                    <div class="flex-1 h-px bg-[var(--border)]"/>
                    <SecondaryButton compact @click="addRow(0)">
                        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
                        {{ t('stationPages.editor.addRow') }}
                    </SecondaryButton>
                    <SecondaryButton
                        v-if="hasClipboard && clipboardType === 'row'"
                        compact
                        @click="onPasteRow(0)"
                    >
                        <font-awesome-icon :icon="['fas', 'paste']" class="mr-1"/>
                        {{ t('stationPages.editor.pasteRow') }}
                    </SecondaryButton>
                    <div class="flex-1 h-px bg-[var(--border)]"/>
                </div>

                <!-- Rows -->
                <div class="space-y-2">
                    <div v-for="(row, index) in rows" :key="row.id + '-' + index">
                        <EditorRow
                            :row="row"
                            :page-id="pageId"
                            :station-uid="stationUid"
                            :preview="preview"
                            :is-first="index === 0"
                            :is-last="index === rows.length - 1"
                            @update:row="updateRow(index, $event)"
                            @delete="deleteRow(index)"
                            @move-up="moveRow(index, -1)"
                            @move-down="moveRow(index, 1)"
                        />

                        <!-- Add row after each row -->
                        <div v-if="!preview" class="flex items-center justify-center gap-2 py-1">
                            <div class="flex-1 h-px bg-[var(--border)]"/>
                            <SecondaryButton compact @click="addRow(index + 1)">
                                <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
                                {{ t('stationPages.editor.addRow') }}
                            </SecondaryButton>
                            <SecondaryButton
                                v-if="hasClipboard && clipboardType === 'row'"
                                compact
                                @click="onPasteRow(index + 1)"
                            >
                                <font-awesome-icon :icon="['fas', 'paste']" class="mr-1"/>
                                {{ t('stationPages.editor.pasteRow') }}
                            </SecondaryButton>
                            <div class="flex-1 h-px bg-[var(--border)]"/>
                        </div>
                    </div>
                </div>
            </template>
        </div>
    </ViewContent>
</template>
