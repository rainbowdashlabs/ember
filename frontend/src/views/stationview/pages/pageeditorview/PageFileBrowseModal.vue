/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Alert from '@/components/feedback/Alert.vue'
import SearchToolbar from './pagefilebrowsemodal/SearchToolbar.vue'
import FilterBar from './pagefilebrowsemodal/FilterBar.vue'
import FilesGrid from './pagefilebrowsemodal/FilesGrid.vue'
import EditMetaModal from './pagefilebrowsemodal/EditMetaModal.vue'
import {
    listPageFolders,
    listPageTags,
    listStationPageFiles,
    pageImageUrl,
    prunePageFiles,
    updatePageFileMeta,
    uploadStationPageFile,
    type PageFile,
    type PageFileFolder,
    type PageFileListing,
    type PageFileTag,
} from '@/api/pageManage'
import type {AxiosError} from 'axios'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {formatSize} from '@/util/format'

const open = defineModel<boolean>('open', {default: false})

const props = defineProps<{
    stationUid: string
    mimePrefix?: string
    multiple?: boolean
}>()

const emit = defineEmits<{
    pick: [{file: PageFile; url: string}]
    pickMany: [Array<{file: PageFile; url: string}>]
}>()

const {t} = useI18n()
const entries = ref<PageFileListing[]>([])
const folders = ref<PageFileFolder[]>([])
const tags = ref<PageFileTag[]>([])
const loading = ref(false)
const uploadError = ref<string | null>(null)
const search = ref('')
const editing = ref<number | null>(null)
const editAlt = ref('')
const editDesc = ref('')
const activeFolder = ref<number | null>(null)
const activeTagFilter = ref<number | null>(null)

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

watch(open, v => { if (v) load() })

const filtered = computed(() => {
    const q = search.value.trim().toLowerCase()
    return entries.value
        .filter(e => !props.mimePrefix || (e.file.mimeType ?? '').startsWith(props.mimePrefix))
        .filter(e => activeFolder.value === null || (e.file.folderId ?? null) === activeFolder.value)
        .filter(e => activeTagFilter.value === null || e.tagIds.includes(activeTagFilter.value))
        .filter(e => !q
            || e.file.fileName.toLowerCase().includes(q)
            || (e.file.mimeType ?? '').toLowerCase().includes(q)
            || (e.file.defaultAltText ?? '').toLowerCase().includes(q)
            || (e.file.defaultDescription ?? '').toLowerCase().includes(q))
})

const unusedCount = computed(() => entries.value.filter(e => !e.inUse).length)

/**
 * Build the {@code accept} attribute for the native file picker so the OS dialog already
 * restricts to the expected type. {@code mimePrefix} is either a category like {@code audio/}
 * (gets a {@code *} wildcard) or a full MIME like {@code application/pdf} (used verbatim with
 * the file-extension hint appended for friendlier OS dialogs).
 */
const acceptAttr = computed<string | undefined>(() => {
    const p = props.mimePrefix
    if (!p) return undefined
    if (p.endsWith('/')) return `${p}*`
    if (p === 'application/pdf') return 'application/pdf,.pdf'
    return p
})

function urlFor(f: PageFile): string {
    return f.contentHash ? pageImageUrl(props.stationUid, f.contentHash) : ''
}

function isImage(f: PageFile): boolean {
    return (f.mimeType ?? '').startsWith('image/')
}

function pick(f: PageFile) {
    emit('pick', {file: f, url: urlFor(f)})
    open.value = false
}

async function onUpload(file: File) {
    await uploadBatch([file])
}

async function onUploadMany(files: File[]) {
    await uploadBatch(files)
}

const {running: uploading, run: uploadBatch} = useAsyncAction(async (picked: File[]) => {
    uploadError.value = null
    let rejected = 0
    const accepted: File[] = []
    for (const f of picked) {
        if (props.mimePrefix && !(f.type || '').startsWith(props.mimePrefix)) {
            rejected++
            continue
        }
        accepted.push(f)
    }
    if (rejected > 0) {
        uploadError.value = t('stationPages.editor.uploadMimeRejected', {
            prefix: props.mimePrefix, count: rejected,
        })
    }
    const stored: PageFile[] = []
    let lastErr: unknown = null
    for (const f of accepted) {
        try {
            const s = await uploadStationPageFile(f)
            stored.push(s)
            entries.value = [{file: s, inUse: false, tagIds: []}, ...entries.value.filter(e => e.file.id !== s.id)]
        } catch (err) {
            lastErr = err
        }
    }
    if (lastErr) {
        const axiosErr = lastErr as AxiosError<{message?: string}>
        uploadError.value = axiosErr.response?.data?.message ?? t('stationPages.editor.uploadFailed')
    }
    const first = stored[0]
    if (!first) return
    if (props.multiple) {
        emit('pickMany', stored.map(f => ({file: f, url: urlFor(f)})))
        open.value = false
    } else {
        pick(first)
    }
})

function startEdit(f: PageFile) {
    editing.value = f.id
    editAlt.value = f.defaultAltText ?? ''
    editDesc.value = f.defaultDescription ?? ''
}

async function saveEdit() {
    if (editing.value == null) return
    const id = editing.value
    try {
        await updatePageFileMeta(id, editAlt.value || null, editDesc.value || null)
        entries.value = entries.value.map(e => e.file.id === id
            ? {...e, file: {...e.file, defaultAltText: editAlt.value, defaultDescription: editDesc.value}}
            : e)
    } finally {
        editing.value = null
    }
}

function cancelEdit() {
    editing.value = null
}

const {running: pruning, run: runPrune} = useAsyncAction(async () => {
    await prunePageFiles()
    await load()
})
</script>

<template>
    <Modal v-model="open" size="xl">
        <div class="space-y-3 flex flex-col h-[80vh]">
            <SubHeader>{{ t('stationPages.editor.browseFiles') }}</SubHeader>
            <SearchToolbar
                v-model:search="search"
                :accept-attr="acceptAttr"
                :uploading="uploading"
                :pruning="pruning"
                :unused-count="unusedCount"
                :multiple="multiple"
                @upload="onUpload"
                @upload-many="onUploadMany"
                @prune="runPrune"
            />
            <Alert v-if="uploadError" variant="error">{{ uploadError }}</Alert>
            <FilterBar
                v-model:active-folder="activeFolder"
                v-model:active-tag-filter="activeTagFilter"
                :folders="folders"
                :tags="tags"
            />
            <FilesGrid
                :loading="loading"
                :filtered="filtered"
                :is-image="isImage"
                :url-for="urlFor"
                :format-size="formatSize"
                @pick="pick"
                @edit="startEdit"
            />
        </div>
        <EditMetaModal
            v-model:editing="editing"
            v-model:edit-alt="editAlt"
            v-model:edit-desc="editDesc"
            @save="saveEdit"
            @cancel="cancelEdit"
        />
    </Modal>
</template>
