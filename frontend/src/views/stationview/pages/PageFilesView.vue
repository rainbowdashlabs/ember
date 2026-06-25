/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import BaseButton from '@/components/button/BaseButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PageFilesSidebar from './pagefilesview/PageFilesSidebar.vue'
import PageFilesGrid from './pagefilesview/PageFilesGrid.vue'
import PageFilesPagination from './pagefilesview/PageFilesPagination.vue'
import PageFilePreviewModal from './pagefilesview/PageFilePreviewModal.vue'
import PageFilesBulkModals from './pagefilesview/PageFilesBulkModals.vue'
import PageFileEditModal from './pagefilesview/PageFileEditModal.vue'
import PageFilesFolderTagModals from './pagefilesview/PageFilesFolderTagModals.vue'
import {useSession} from '@/composables/useSession'
import {
    assignPageTag,
    createPageFolder,
    createPageTag,
    deletePageFolder,
    deletePageTag,
    deleteStationPageFile,
    listPageFolders,
    listPageTags,
    listStationPageFiles,
    moveFileToFolder,
    prunePageFiles,
    unassignPageTag,
    updatePageFileMeta,
    updatePageFolder,
    updatePageTag,
    uploadStationPageFile,
    type PageFile,
    type PageFileFolder,
    type PageFileListing,
    type PageFileTag,
} from '@/api/pageManage'

const {t} = useI18n()
const {sessionInfo} = useSession()

const entries = ref<PageFileListing[]>([])
const folders = ref<PageFileFolder[]>([])
const tags = ref<PageFileTag[]>([])
const loading = ref(false)
const uploading = ref(false)
const uploadError = ref<string | null>(null)
const pruning = ref(false)
const search = ref('')
const activeFolder = ref<number | null>(null)
const activeTagFilter = ref<number | null>(null)
const selectedIds = ref<number[]>([])

const editing = ref<PageFile | null>(null)

const folderModalOpen = ref(false)
const folderName = ref('')
const folderParent = ref<number | null>(null)
const editingFolder = ref<PageFileFolder | null>(null)

const tagModalOpen = ref(false)
const tagName = ref('')
const tagColor = ref('#888888')
const editingTag = ref<PageFileTag | null>(null)

const bulkMoveOpen = ref(false)
const bulkMoveTarget = ref<number | null>(null)
const bulkDeleteOpen = ref(false)

const multiSelect = ref(false)
const lastCheckedIndex = ref<number | null>(null)

const PAGE_SIZE_KEY = 'stationPages.filesPerPage'
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const
function loadStoredPageSize(): number {
    const raw = typeof window !== 'undefined' ? window.localStorage?.getItem(PAGE_SIZE_KEY) : null
    const parsed = raw ? parseInt(raw, 10) : NaN
    return (PAGE_SIZE_OPTIONS as readonly number[]).includes(parsed) ? parsed : 20
}
const pageSize = ref<number>(loadStoredPageSize())
const currentPage = ref(1)
watch(pageSize, (v) => {
    if (typeof window !== 'undefined') window.localStorage?.setItem(PAGE_SIZE_KEY, String(v))
    currentPage.value = 1
})

const previewFile = ref<PageFile | null>(null)

const stationUid = computed(() => sessionInfo.value?.stationId ?? '')

function openPreview(f: PageFile) {
    previewFile.value = f
}

async function load() {
    loading.value = true
    try {
        const [files, fs, ts] = await Promise.all([listStationPageFiles(), listPageFolders(), listPageTags()])
        entries.value = files
        folders.value = fs
        tags.value = ts
    } catch {
        entries.value = []
        folders.value = []
        tags.value = []
    } finally {
        loading.value = false
    }
}

onMounted(load)

watch(activeFolder, () => { selectedIds.value = []; lastCheckedIndex.value = null; currentPage.value = 1 })
watch(activeTagFilter, () => { currentPage.value = 1 })
watch(search, () => { currentPage.value = 1 })

const folderTree = computed(() => {
    const byId = new Map<number, PageFileFolder & {children: PageFileFolder[]}>()
    folders.value.forEach(f => byId.set(f.id, {...f, children: []}))
    const roots: Array<PageFileFolder & {children: PageFileFolder[]}> = []
    byId.forEach(node => {
        if (node.parentId != null && byId.has(node.parentId)) byId.get(node.parentId)!.children.push(node)
        else roots.push(node)
    })
    return roots
})

const folderById = computed(() => {
    const m = new Map<number, PageFileFolder>()
    folders.value.forEach(f => m.set(f.id, f))
    return m
})

const visibleFolders = computed(() =>
    folders.value
        .filter(f => (f.parentId ?? null) === activeFolder.value)
        .sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name)))

const breadcrumbs = computed(() => {
    const trail: PageFileFolder[] = []
    let curId: number | null | undefined = activeFolder.value
    while (curId != null) {
        const f = folderById.value.get(curId)
        if (!f) break
        trail.unshift(f)
        curId = f.parentId
    }
    return trail
})

const filtered = computed(() => {
    const q = search.value.trim().toLowerCase()
    return entries.value.filter(e => {
        if ((e.file.folderId ?? null) !== activeFolder.value) return false
        if (activeTagFilter.value !== null && !e.tagIds.includes(activeTagFilter.value)) return false
        if (!q) return true
        return e.file.fileName.toLowerCase().includes(q)
            || (e.file.mimeType ?? '').toLowerCase().includes(q)
            || (e.file.defaultAltText ?? '').toLowerCase().includes(q)
            || (e.file.defaultDescription ?? '').toLowerCase().includes(q)
    })
})

const unusedCount = computed(() => entries.value.filter(e => !e.inUse).length)

const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))
const pagedFiles = computed(() => {
    const start = (currentPage.value - 1) * pageSize.value
    return filtered.value.slice(start, start + pageSize.value)
})
watch(totalPages, (max) => {
    if (currentPage.value > max) currentPage.value = max
})

function toggleSelected(id: number, value: boolean, shift: boolean, index: number) {
    if (shift && lastCheckedIndex.value !== null && lastCheckedIndex.value !== index) {
        const lo = Math.min(lastCheckedIndex.value, index)
        const hi = Math.max(lastCheckedIndex.value, index)
        const rangeIds = pagedFiles.value.slice(lo, hi + 1).map(e => e.file.id)
        if (value) {
            const merged = new Set(selectedIds.value)
            rangeIds.forEach(rid => merged.add(rid))
            selectedIds.value = Array.from(merged)
        } else {
            selectedIds.value = selectedIds.value.filter(x => !rangeIds.includes(x))
        }
    } else if (value) {
        if (!selectedIds.value.includes(id)) selectedIds.value = [...selectedIds.value, id]
    } else {
        selectedIds.value = selectedIds.value.filter(x => x !== id)
    }
    lastCheckedIndex.value = index
}

function clearSelection() {
    selectedIds.value = []
    lastCheckedIndex.value = null
}

function toggleMultiSelect() {
    multiSelect.value = !multiSelect.value
    if (!multiSelect.value) clearSelection()
}

async function uploadMany(files: File[]) {
    uploading.value = true
    uploadError.value = null
    let failed = 0
    for (const f of files) {
        try {
            const stored = await uploadStationPageFile(f)
            const placed = activeFolder.value != null
                ? (await moveFileToFolder(stored.id, activeFolder.value), {...stored, folderId: activeFolder.value})
                : stored
            entries.value = [
                {file: placed, inUse: false, tagIds: []},
                ...entries.value.filter(e => e.file.id !== placed.id),
            ]
        } catch {
            failed++
        }
    }
    if (failed > 0) uploadError.value = t('fileUpload.uploadFailed')
    uploading.value = false
}

async function runPrune() {
    if (!confirm(t('stationPages.editor.prunePrompt', {count: unusedCount.value}))) return
    pruning.value = true
    try {
        await prunePageFiles()
        await load()
    } finally {
        pruning.value = false
    }
}

function startEdit(f: PageFile) {
    editing.value = f
}

async function saveEdit(id: number, altText: string, description: string) {
    await updatePageFileMeta(id, altText || null, description || null)
    entries.value = entries.value.map(e => e.file.id === id
        ? {...e, file: {...e.file, defaultAltText: altText, defaultDescription: description}}
        : e)
}

async function toggleFileTag(fileId: number, tagId: number, currentlyAssigned: boolean) {
    if (currentlyAssigned) await unassignPageTag(fileId, tagId)
    else await assignPageTag(fileId, tagId)
    entries.value = entries.value.map(e => {
        if (e.file.id !== fileId) return e
        const next = currentlyAssigned ? e.tagIds.filter(t => t !== tagId) : [...e.tagIds, tagId]
        return {...e, tagIds: next}
    })
}

function openFolderModal(parent: PageFileFolder | null) {
    folderName.value = ''
    folderParent.value = parent?.id ?? activeFolder.value
    editingFolder.value = null
    folderModalOpen.value = true
}

function openFolderEdit(f: PageFileFolder) {
    folderName.value = f.name
    folderParent.value = f.parentId
    editingFolder.value = f
    folderModalOpen.value = true
}

async function saveFolder() {
    if (!folderName.value.trim()) return
    if (editingFolder.value) {
        await updatePageFolder(
            editingFolder.value.id, folderName.value, folderParent.value, editingFolder.value.sortOrder)
    } else {
        await createPageFolder(folderName.value, folderParent.value)
    }
    folderModalOpen.value = false
    folders.value = await listPageFolders()
}

async function removeFolder(f: PageFileFolder) {
    if (!confirm(t('stationPages.editor.folderDeletePrompt', {name: f.name}))) return
    await deletePageFolder(f.id)
    if (activeFolder.value === f.id) activeFolder.value = f.parentId ?? null
    await load()
}

function openTagModal() {
    tagName.value = ''
    tagColor.value = '#888888'
    editingTag.value = null
    tagModalOpen.value = true
}

function openTagEdit(tag: PageFileTag) {
    tagName.value = tag.name
    tagColor.value = tag.color ?? '#888888'
    editingTag.value = tag
    tagModalOpen.value = true
}

async function saveTag() {
    if (!tagName.value.trim()) return
    if (editingTag.value) await updatePageTag(editingTag.value.id, tagName.value, tagColor.value)
    else await createPageTag(tagName.value, tagColor.value)
    tagModalOpen.value = false
    tags.value = await listPageTags()
}

async function removeTag(tag: PageFileTag) {
    if (!confirm(t('stationPages.editor.tagDeletePrompt', {name: tag.name}))) return
    await deletePageTag(tag.id)
    if (activeTagFilter.value === tag.id) activeTagFilter.value = null
    await load()
}

function openBulkMove() {
    bulkMoveTarget.value = activeFolder.value
    bulkMoveOpen.value = true
}

async function runBulkMove() {
    const target = bulkMoveTarget.value
    const ids = [...selectedIds.value]
    for (const id of ids) {
        await moveFileToFolder(id, target)
    }
    entries.value = entries.value.map(e => ids.includes(e.file.id)
        ? {...e, file: {...e.file, folderId: target}}
        : e)
    bulkMoveOpen.value = false
    selectedIds.value = []
}

async function deleteOne(file: PageFile) {
    if (!confirm(t('stationPages.editor.deleteFilePrompt', {name: file.fileName}))) return
    await deleteStationPageFile(file.id)
    entries.value = entries.value.filter(e => e.file.id !== file.id)
    selectedIds.value = selectedIds.value.filter(x => x !== file.id)
}

async function runBulkDelete() {
    const ids = [...selectedIds.value]
    bulkDeleteOpen.value = false
    for (const id of ids) {
        try {
            await deleteStationPageFile(id)
        } catch {
            /* keep going — the load() at the end re-syncs */
        }
    }
    selectedIds.value = []
    lastCheckedIndex.value = null
    await load()
}

</script>

<template>
    <ViewContent>
        <div class="space-y-4">
        <SectionHeader>{{ t('stationPages.editor.filesTitle') }}</SectionHeader>

        <div class="grid grid-cols-1 lg:grid-cols-[260px_1fr] gap-4">
            <PageFilesSidebar :folder-tree="folderTree" :tags="tags"
                              :active-folder="activeFolder" :active-tag-filter="activeTagFilter"
                              @update:active-folder="(id: number | null) => activeFolder = id"
                              @update:active-tag-filter="(id: number | null) => activeTagFilter = id"
                              @new-folder="openFolderModal(null)"
                              @edit-folder="openFolderEdit" @remove-folder="removeFolder"
                              @new-tag="openTagModal"
                              @edit-tag="openTagEdit" @remove-tag="removeTag"/>

            <div class="space-y-4">
                <NeutralContainer class="flex flex-wrap items-center gap-2">
                    <TextInput v-model="search" :placeholder="t('stationPages.editor.browseFilesSearch')"
                               class="flex-1 min-w-[200px]"/>
                    <div class="flex flex-wrap items-center gap-2">
                        <IconButton :icon="['fas', multiSelect ? 'square-check' : 'square']"
                                    :label="multiSelect ? t('stationPages.editor.multiSelectDisable') : t('stationPages.editor.multiSelectEnable')"
                                    :class="multiSelect ? '!text-primary' : ''"
                                    @click="toggleMultiSelect"/>
                        <FileUploadButton :disabled="uploading" multiple
                                          @select="(f: File) => uploadMany([f])"
                                          @select-many="(fs: File[]) => uploadMany(fs)">
                            {{ uploading ? t('common.loading') : t('stationPages.editor.uploadNewFile') }}
                        </FileUploadButton>
                        <ErrorButton :disabled="pruning || unusedCount === 0" @click="runPrune">
                            <font-awesome-icon :icon="['fas', 'broom']" class="mr-1"/>
                            {{ pruning ? t('common.loading') : t('stationPages.editor.pruneUnused', {count: unusedCount}) }}
                        </ErrorButton>
                    </div>
                </NeutralContainer>

                <nav class="flex items-center gap-1 text-sm flex-wrap">
                    <BaseButton compact class="!font-normal hover:bg-(--bg-accent)"
                                :class="activeFolder === null ? '!text-primary !font-medium' : '!text-(--text-muted)'"
                                @click="activeFolder = null">
                        <font-awesome-icon :icon="['fas', 'house']" class="mr-1"/>
                        {{ t('stationPages.editor.root') }}
                    </BaseButton>
                    <template v-for="(b, i) in breadcrumbs" :key="b.id">
                        <span class="text-(--text-muted)">/</span>
                        <BaseButton compact class="!font-normal hover:bg-(--bg-accent)"
                                    :class="i === breadcrumbs.length - 1 ? '!text-primary !font-medium' : '!text-(--text-muted)'"
                                    @click="activeFolder = b.id">{{ b.name }}</BaseButton>
                    </template>
                </nav>

                <NeutralContainer v-if="selectedIds.length > 0" class="flex flex-wrap items-center gap-2 !py-2">
                    <span class="text-sm">{{ t('stationPages.editor.selectedCount', {count: selectedIds.length}) }}</span>
                    <SecondaryButton @click="openBulkMove">
                        <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="mr-1"/>
                        {{ t('stationPages.editor.moveSelected') }}
                    </SecondaryButton>
                    <ErrorButton @click="bulkDeleteOpen = true">
                        <font-awesome-icon :icon="['fas', 'trash']" class="mr-1"/>
                        {{ t('stationPages.editor.deleteSelected') }}
                    </ErrorButton>
                    <SecondaryButton @click="clearSelection">{{ t('stationPages.editor.clearSelection') }}</SecondaryButton>
                </NeutralContainer>

                <Alert v-if="uploadError" variant="error">{{ uploadError }}</Alert>

                <div v-if="loading" class="flex justify-center py-8"><Spinner size="lg"/></div>
                <p v-else-if="visibleFolders.length === 0 && filtered.length === 0" class="text-sm text-(--text-muted) text-center py-8">
                    {{ t('stationPages.editor.browseFilesEmpty') }}
                </p>
                <template v-else>
                    <PageFilesGrid :folders="visibleFolders" :files="pagedFiles" :tags="tags"
                                   :selected-ids="selectedIds" :station-uid="stationUid"
                                   :multi-select="multiSelect"
                                   @open-folder="(id: number) => activeFolder = id"
                                   @preview-file="openPreview"
                                   @edit-file="startEdit"
                                   @delete-file="deleteOne"
                                   @toggle-select="toggleSelected"
                                   @toggle-tag="toggleFileTag"/>

                    <PageFilesPagination :current-page="currentPage" :total-pages="totalPages"
                                        :page-size="pageSize" :page-size-options="PAGE_SIZE_OPTIONS"
                                        @update:current-page="(v: number) => currentPage = v"
                                        @update:page-size="(v: number) => pageSize = v"/>
                </template>
            </div>
        </div>

        <PageFileEditModal v-model="editing" @save="saveEdit"/>

        <PageFilesFolderTagModals v-model:folder-open="folderModalOpen" v-model:folder-name="folderName"
                                  v-model:folder-parent="folderParent" v-model:tag-open="tagModalOpen"
                                  v-model:tag-name="tagName" v-model:tag-color="tagColor"
                                  :folders="folders" :editing-folder="editingFolder" :editing-tag="editingTag"
                                  @save-folder="saveFolder" @save-tag="saveTag"/>

        <PageFilePreviewModal :file="previewFile" :station-uid="stationUid" @close="previewFile = null"/>

        <PageFilesBulkModals v-model:move-open="bulkMoveOpen" v-model:delete-open="bulkDeleteOpen"
                            v-model:move-target="bulkMoveTarget"
                            :selected-count="selectedIds.length" :folders="folders"
                            @move="runBulkMove" @delete="runBulkDelete"/>
        </div>
    </ViewContent>
</template>
