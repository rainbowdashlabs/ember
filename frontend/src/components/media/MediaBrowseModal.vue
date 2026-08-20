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
import SearchToolbar from './mediabrowsemodal/SearchToolbar.vue'
import FilterBar from './mediabrowsemodal/FilterBar.vue'
import FilesGrid from './mediabrowsemodal/FilesGrid.vue'
import MediaFileEditModal from '@/components/media/MediaFileEditModal.vue'
import {mediaFileUrl, pruneMediaFiles, updateMediaFileMeta, uploadMediaFile, type StationFile} from '@/api/media'
import {useMediaLibrary} from '@/composables/useMediaLibrary'
import type {AxiosError} from 'axios'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useSession} from '@/composables/useSession'
import {StationPermission} from '@/api/types'
import {formatSize} from '@/util/format'

const open = defineModel<boolean>('open', {default: false})

const props = defineProps<{
    stationUid: string
    mimePrefix?: string
    multiple?: boolean
}>()

const emit = defineEmits<{
    pick: [{file: StationFile; url: string}]
    pickMany: [Array<{file: StationFile; url: string}>]
}>()

const {t} = useI18n()
const {hasPermission} = useSession()
const {entries, folders, tags, loading, load} = useMediaLibrary()

/**
 * A member who authors none of the station's content sees only their own uploads, and organising
 * station media is not theirs to do. Same component either way: the backend decides which set
 * comes back, and these two flags decide which controls come with it.
 */
const organises = computed(() => hasPermission(StationPermission.PAGE_EDIT)
    || hasPermission(StationPermission.NEWS_EDIT)
    || hasPermission(StationPermission.KNOWLEDGE_EDIT))
const mayPrune = computed(() => hasPermission(StationPermission.PAGE_MANAGER))

const uploadError = ref<string | null>(null)
const search = ref('')
const editing = ref<StationFile | null>(null)
const activeFolder = ref<number | null>(null)
const activeTagFilter = ref<number | null>(null)

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

function urlFor(f: StationFile): string {
    return f.contentHash ? mediaFileUrl(props.stationUid, f.contentHash) : ''
}

function isImage(f: StationFile): boolean {
    return (f.mimeType ?? '').startsWith('image/')
}

function pick(f: StationFile) {
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
    const stored: StationFile[] = []
    let lastErr: unknown = null
    for (const f of accepted) {
        try {
            const s = await uploadMediaFile(f)
            stored.push(s)
            entries.value = [{file: s, inUse: false, tagIds: [], uploadedBy: null}, ...entries.value.filter(e => e.file.id !== s.id)]
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

function startEdit(f: StationFile) {
    editing.value = f
}

async function saveEdit(id: number, altText: string, description: string) {
    await updateMediaFileMeta(id, altText || null, description || null)
    entries.value = entries.value.map(e => e.file.id === id
        ? {...e, file: {...e.file, defaultAltText: altText, defaultDescription: description}}
        : e)
}

const {running: pruning, run: runPrune} = useAsyncAction(async () => {
    await pruneMediaFiles()
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
                :may-prune="mayPrune"
                :unused-count="unusedCount"
                :multiple="multiple"
                @upload="onUpload"
                @upload-many="onUploadMany"
                @prune="runPrune"
            />
            <Alert v-if="uploadError" variant="error">{{ uploadError }}</Alert>
            <FilterBar
                v-if="organises"
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
        <MediaFileEditModal v-model="editing" @save="saveEdit"/>
    </Modal>
</template>
