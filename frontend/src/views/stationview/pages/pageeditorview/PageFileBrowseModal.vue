/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import BaseButton from '@/components/button/BaseButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
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
const uploading = ref(false)
const uploadError = ref<string | null>(null)
const search = ref('')
const editing = ref<number | null>(null)
const editAlt = ref('')
const editDesc = ref('')
const pruning = ref(false)
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

async function uploadBatch(picked: File[]) {
    uploading.value = true
    uploadError.value = null
    // The native file picker's `accept` attribute is only a hint — the user can still pick "All
    // files" in the OS dialog. Enforce the expected MIME prefix here so an audio cell never
    // ends up with a PDF (or worse). Rejected files surface in the error banner.
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
            entries.value = [{file: s, inUse: false}, ...entries.value.filter(e => e.file.id !== s.id)]
        } catch (err) {
            lastErr = err
        }
    }
    uploading.value = false
    if (lastErr) {
        const axiosErr = lastErr as AxiosError<{message?: string}>
        uploadError.value = axiosErr.response?.data?.message ?? t('stationPages.editor.uploadFailed')
    }
    if (stored.length === 0) return
    if (props.multiple) {
        emit('pickMany', stored.map(f => ({file: f, url: urlFor(f)})))
        open.value = false
    } else {
        pick(stored[0])
    }
}

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

async function runPrune() {
    pruning.value = true
    try {
        await prunePageFiles()
        await load()
    } finally {
        pruning.value = false
    }
}

function formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
    <Modal v-model="open" size="xl">
        <div class="space-y-3 flex flex-col h-[80vh]">
            <SectionHeader>{{ t('stationPages.editor.browseFiles') }}</SectionHeader>
            <div class="flex flex-col sm:flex-row gap-2">
                <TextInput
                    v-model="search"
                    :placeholder="t('stationPages.editor.browseFilesSearch')"
                    class="flex-1"
                />
                <FileUploadButton
                    :accept="acceptAttr"
                    :disabled="uploading"
                    :multiple="multiple"
                    @select="onUpload"
                    @select-many="onUploadMany"
                >
                    {{ uploading ? t('common.loading') : t('stationPages.editor.uploadNewFile') }}
                </FileUploadButton>
                <ErrorButton :disabled="pruning || unusedCount === 0" @click="runPrune">
                    <font-awesome-icon :icon="['fas', 'broom']" class="mr-1"/>
                    {{ pruning ? t('common.loading') : t('stationPages.editor.pruneUnused', {count: unusedCount}) }}
                </ErrorButton>
            </div>
            <Alert v-if="uploadError" variant="error">{{ uploadError }}</Alert>
            <div class="flex flex-wrap items-center gap-2 text-xs">
                <SelectInput :model-value="activeFolder === null ? '' : String(activeFolder)"
                             class="!px-2 !py-1"
                             @update:model-value="(v: string) => activeFolder = v ? +v : null">
                    <option value="">{{ t('stationPages.editor.allFiles') }}</option>
                    <option v-for="f in folders" :key="f.id" :value="f.id">{{ f.name }}</option>
                </SelectInput>
                <BaseButton compact class="!rounded-full !border !font-normal"
                            :class="activeTagFilter === null ? '!border-primary !text-primary' : '!border-(--border) !text-(--text-muted)'"
                            @click="activeTagFilter = null">{{ t('stationPages.editor.allTags') }}</BaseButton>
                <BaseButton v-for="tag in tags" :key="tag.id" compact
                            class="!rounded-full !border !font-normal"
                            :style="activeTagFilter === tag.id ? {borderColor: tag.color ?? 'var(--primary)', color: tag.color ?? 'var(--primary)'} : {}"
                            :class="activeTagFilter === tag.id ? '' : '!border-(--border) !text-(--text-muted)'"
                            @click="activeTagFilter = activeTagFilter === tag.id ? null : tag.id">
                    <span class="inline-block w-2 h-2 rounded-full mr-1" :style="{background: tag.color ?? '#888'}"/>
                    {{ tag.name }}
                </BaseButton>
            </div>
            <div class="flex-1 min-h-0 overflow-y-auto">
                <div v-if="loading" class="flex items-center justify-center py-8">
                    <Spinner size="md"/>
                </div>
                <p v-else-if="filtered.length === 0" class="text-sm text-(--text-muted) text-center py-8">
                    {{ t('stationPages.editor.browseFilesEmpty') }}
                </p>
                <div v-else class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-3">
                    <div
                        v-for="e in filtered" :key="e.file.id"
                        class="group relative flex flex-col rounded-theme border overflow-hidden text-left"
                        :class="e.inUse ? 'border-(--border)' : 'border-error/50 bg-error/5'"
                    >
                        <button
                            type="button"
                            class="flex-1 text-left hover:bg-primary/5 transition-colors"
                            @click="pick(e.file)"
                        >
                            <div class="aspect-square w-full bg-(--bg-accent) flex items-center justify-center overflow-hidden">
                                <img
                                    v-if="isImage(e.file)"
                                    :src="urlFor(e.file)"
                                    :alt="e.file.defaultAltText ?? e.file.fileName"
                                    loading="lazy"
                                    class="w-full h-full object-cover"
                                />
                                <font-awesome-icon v-else :icon="['fas', 'file']" class="text-3xl text-(--text-muted)"/>
                            </div>
                            <div class="p-2 text-xs space-y-0.5 min-w-0">
                                <p class="truncate font-medium" :title="e.file.fileName">{{ e.file.fileName }}</p>
                                <p class="text-(--text-muted)">{{ formatSize(e.file.fileSize) }}</p>
                                <p v-if="e.file.defaultAltText" class="truncate italic" :title="e.file.defaultAltText">
                                    {{ e.file.defaultAltText }}
                                </p>
                            </div>
                        </button>
                        <div class="absolute top-1 right-1 flex items-center gap-1">
                            <span v-if="!e.inUse"
                                  class="text-[10px] uppercase tracking-wider bg-error text-white rounded px-1.5 py-0.5">
                                {{ t('stationPages.editor.unusedBadge') }}
                            </span>
                            <IconButton
                                :icon="['fas', 'pen']"
                                :label="t('stationPages.editor.editFileMeta')"
                                class="bg-(--bg)/90 backdrop-blur-sm rounded-full !p-1 text-xs"
                                @click="startEdit(e.file)"
                            />
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <Modal v-if="editing != null" :model-value="editing != null" size="md"
               @update:model-value="(v: boolean) => { if (!v) cancelEdit() }">
            <div class="space-y-3">
                <SectionHeader>{{ t('stationPages.editor.editFileMeta') }}</SectionHeader>
                <TextInput v-model="editAlt" :placeholder="t('stationPages.editor.altText')"/>
                <TextInput v-model="editDesc" :placeholder="t('stationPages.editor.imageDescription')"/>
                <div class="flex justify-end gap-2">
                    <SecondaryButton @click="cancelEdit">{{ t('common.cancel') }}</SecondaryButton>
                    <PrimaryButton @click="saveEdit">{{ t('common.save') }}</PrimaryButton>
                </div>
            </div>
        </Modal>
    </Modal>
</template>
