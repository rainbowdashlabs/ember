/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import BaseButton from '@/components/button/BaseButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ColorInput from '@/components/input/ColorInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import PageFilesSidebar from './pagefilesview/PageFilesSidebar.vue'
import PageFilesGrid from './pagefilesview/PageFilesGrid.vue'
import {useSession} from '@/composables/useSession'
import {
    assignPageTag,
    createPageFolder,
    createPageTag,
    deletePageFolder,
    deletePageTag,
    listPageFolders,
    listPageTags,
    listStationPageFiles,
    moveFileToFolder,
    pageImageUrl,
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
const editAlt = ref('')
const editDesc = ref('')

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

const previewFile = ref<PageFile | null>(null)

const stationUid = computed(() => sessionInfo.value?.stationId ?? '')

const previewUrl = computed(() => {
    const f = previewFile.value
    return f?.contentHash && stationUid.value ? pageImageUrl(stationUid.value, f.contentHash) : ''
})

const previewKind = computed<'image' | 'video' | 'audio' | 'pdf' | 'other'>(() => {
    const m = previewFile.value?.mimeType ?? ''
    if (m.startsWith('image/')) return 'image'
    if (m.startsWith('video/')) return 'video'
    if (m.startsWith('audio/')) return 'audio'
    if (m === 'application/pdf') return 'pdf'
    return 'other'
})

function openPreview(f: PageFile) {
    previewFile.value = f
}

function formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`
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

watch(activeFolder, () => { selectedIds.value = [] })

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

function toggleSelected(id: number, value: boolean) {
    if (value) {
        if (!selectedIds.value.includes(id)) selectedIds.value = [...selectedIds.value, id]
    } else {
        selectedIds.value = selectedIds.value.filter(x => x !== id)
    }
}

function clearSelection() {
    selectedIds.value = []
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
    editAlt.value = f.defaultAltText ?? ''
    editDesc.value = f.defaultDescription ?? ''
}

async function saveEdit() {
    if (!editing.value) return
    const id = editing.value.id
    await updatePageFileMeta(id, editAlt.value || null, editDesc.value || null)
    entries.value = entries.value.map(e => e.file.id === id
        ? {...e, file: {...e.file, defaultAltText: editAlt.value, defaultDescription: editDesc.value}}
        : e)
    editing.value = null
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
                <NeutralContainer class="flex flex-col sm:flex-row gap-2 items-center">
                    <TextInput v-model="search" :placeholder="t('stationPages.editor.browseFilesSearch')" class="flex-1"/>
                    <FileUploadButton :disabled="uploading" multiple
                                      @select="(f: File) => uploadMany([f])"
                                      @select-many="(fs: File[]) => uploadMany(fs)">
                        {{ uploading ? t('common.loading') : t('stationPages.editor.uploadNewFile') }}
                    </FileUploadButton>
                    <ErrorButton :disabled="pruning || unusedCount === 0" @click="runPrune">
                        <font-awesome-icon :icon="['fas', 'broom']" class="mr-1"/>
                        {{ pruning ? t('common.loading') : t('stationPages.editor.pruneUnused', {count: unusedCount}) }}
                    </ErrorButton>
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

                <NeutralContainer v-if="selectedIds.length > 0" class="flex items-center gap-2 !py-2">
                    <span class="text-sm">{{ t('stationPages.editor.selectedCount', {count: selectedIds.length}) }}</span>
                    <SecondaryButton @click="openBulkMove">
                        <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="mr-1"/>
                        {{ t('stationPages.editor.moveSelected') }}
                    </SecondaryButton>
                    <SecondaryButton @click="clearSelection">{{ t('stationPages.editor.clearSelection') }}</SecondaryButton>
                </NeutralContainer>

                <Alert v-if="uploadError" variant="error">{{ uploadError }}</Alert>

                <div v-if="loading" class="flex justify-center py-8"><Spinner size="lg"/></div>
                <p v-else-if="visibleFolders.length === 0 && filtered.length === 0" class="text-sm text-(--text-muted) text-center py-8">
                    {{ t('stationPages.editor.browseFilesEmpty') }}
                </p>
                <PageFilesGrid v-else :folders="visibleFolders" :files="filtered" :tags="tags"
                               :selected-ids="selectedIds" :station-uid="stationUid"
                               @open-folder="(id: number) => activeFolder = id"
                               @preview-file="openPreview"
                               @edit-file="startEdit"
                               @toggle-select="toggleSelected"
                               @toggle-tag="toggleFileTag"/>
            </div>
        </div>

        <Modal v-if="editing" :model-value="!!editing" size="md"
               @update:model-value="(v: boolean) => { if (!v) editing = null }">
            <div class="space-y-3">
                <SectionHeader>{{ t('stationPages.editor.editFileMeta') }}</SectionHeader>
                <TextInput v-model="editAlt" :placeholder="t('stationPages.editor.altText')"/>
                <TextInput v-model="editDesc" :placeholder="t('stationPages.editor.imageDescription')"/>
                <div class="flex justify-end gap-2">
                    <SecondaryButton @click="editing = null">{{ t('common.cancel') }}</SecondaryButton>
                    <PrimaryButton @click="saveEdit">{{ t('common.save') }}</PrimaryButton>
                </div>
            </div>
        </Modal>

        <Modal v-model="folderModalOpen" size="md">
            <div class="space-y-3">
                <SectionHeader>{{ editingFolder ? t('stationPages.editor.editFolder') : t('stationPages.editor.newFolder') }}</SectionHeader>
                <TextInput v-model="folderName" :placeholder="t('stationPages.editor.folderName')"/>
                <SelectInput :model-value="folderParent === null ? '' : String(folderParent)"
                             class="w-full"
                             @update:model-value="(v: string) => folderParent = v ? +v : null">
                    <option value="">{{ t('stationPages.editor.rootFolder') }}</option>
                    <option v-for="f in folders" :key="f.id"
                            :value="f.id" :disabled="editingFolder?.id === f.id">
                        {{ f.name }}
                    </option>
                </SelectInput>
                <div class="flex justify-end gap-2">
                    <SecondaryButton @click="folderModalOpen = false">{{ t('common.cancel') }}</SecondaryButton>
                    <PrimaryButton @click="saveFolder">{{ t('common.save') }}</PrimaryButton>
                </div>
            </div>
        </Modal>

        <Modal v-model="tagModalOpen" size="md">
            <div class="space-y-3">
                <SectionHeader>{{ editingTag ? t('stationPages.editor.editTag') : t('stationPages.editor.newTag') }}</SectionHeader>
                <TextInput v-model="tagName" :placeholder="t('stationPages.editor.tagName')"/>
                <div class="flex items-center gap-2">
                    <ColorInput v-model="tagColor"/>
                    <span class="text-xs text-(--text-muted)">{{ tagColor }}</span>
                </div>
                <div class="flex justify-end gap-2">
                    <SecondaryButton @click="tagModalOpen = false">{{ t('common.cancel') }}</SecondaryButton>
                    <PrimaryButton @click="saveTag">{{ t('common.save') }}</PrimaryButton>
                </div>
            </div>
        </Modal>

        <Modal v-if="previewFile" :model-value="!!previewFile" size="xl"
               @update:model-value="(v: boolean) => { if (!v) previewFile = null }">
            <div class="space-y-3">
                <SectionHeader>{{ previewFile.fileName }}</SectionHeader>
                <div class="flex items-center justify-center bg-(--bg-accent) rounded-theme overflow-hidden max-h-[70vh]">
                    <img v-if="previewKind === 'image'" :src="previewUrl"
                         :alt="previewFile.defaultAltText ?? previewFile.fileName"
                         class="max-w-full max-h-[70vh] object-contain"/>
                    <video v-else-if="previewKind === 'video'" :src="previewUrl"
                           controls class="max-w-full max-h-[70vh]"/>
                    <audio v-else-if="previewKind === 'audio'" :src="previewUrl"
                           controls class="w-full"/>
                    <iframe v-else-if="previewKind === 'pdf'" :src="previewUrl"
                            class="w-full h-[70vh]"/>
                    <div v-else class="flex flex-col items-center gap-2 p-8 text-(--text-muted)">
                        <font-awesome-icon :icon="['fas', 'file']" class="text-5xl"/>
                        <p class="text-sm">{{ previewFile.mimeType ?? '—' }}</p>
                    </div>
                </div>
                <div class="flex items-center justify-between text-xs text-(--text-muted)">
                    <span>{{ formatSize(previewFile.fileSize) }}</span>
                    <a :href="previewUrl" target="_blank" rel="noopener noreferrer" class="hover:text-primary">
                        <font-awesome-icon :icon="['fas', 'expand']" class="mr-1"/>
                        {{ t('common.open') }}
                    </a>
                </div>
            </div>
        </Modal>

        <Modal v-model="bulkMoveOpen" size="md">
            <div class="space-y-3">
                <SectionHeader>{{ t('stationPages.editor.moveToFolder') }}</SectionHeader>
                <p class="text-sm text-(--text-muted)">{{ t('stationPages.editor.selectedCount', {count: selectedIds.length}) }}</p>
                <SelectInput :model-value="bulkMoveTarget === null ? '' : String(bulkMoveTarget)"
                             class="w-full"
                             @update:model-value="(v: string) => bulkMoveTarget = v ? +v : null">
                    <option value="">{{ t('stationPages.editor.rootFolder') }}</option>
                    <option v-for="f in folders" :key="f.id" :value="f.id">{{ f.name }}</option>
                </SelectInput>
                <div class="flex justify-end gap-2">
                    <SecondaryButton @click="bulkMoveOpen = false">{{ t('common.cancel') }}</SecondaryButton>
                    <PrimaryButton @click="runBulkMove">{{ t('stationPages.editor.moveSelected') }}</PrimaryButton>
                </div>
            </div>
        </Modal>
        </div>
    </ViewContent>
</template>
