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
import {listStationPageFiles, pageImageUrl, type PageFile} from '@/api/pageManage'

const open = defineModel<boolean>('open', {default: false})

const props = defineProps<{
    stationUid: string
    /** If set, only show files whose mime starts with this prefix (e.g. 'image/'). */
    mimePrefix?: string
}>()

const emit = defineEmits<{
    /** Fires when the user picks a file. Provides both the file metadata and the public URL. */
    pick: [{file: PageFile; url: string}]
}>()

const {t} = useI18n()
const files = ref<PageFile[]>([])
const loading = ref(false)
const search = ref('')

async function load() {
    loading.value = true
    try {
        files.value = await listStationPageFiles()
    } catch {
        files.value = []
    } finally {
        loading.value = false
    }
}

// Refresh whenever the modal opens — the user may have uploaded new files in another tab.
watch(open, v => {
    if (v) load()
})

const filtered = computed(() => {
    const q = search.value.trim().toLowerCase()
    return files.value
        .filter(f => !props.mimePrefix || (f.mimeType ?? '').startsWith(props.mimePrefix))
        .filter(f => !q || f.fileName.toLowerCase().includes(q) || (f.mimeType ?? '').toLowerCase().includes(q))
})

function urlFor(f: PageFile): string {
    return pageImageUrl(props.stationUid, f.id)
}

function isImage(f: PageFile): boolean {
    return (f.mimeType ?? '').startsWith('image/')
}

function pick(f: PageFile) {
    emit('pick', {file: f, url: urlFor(f)})
    open.value = false
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
            <TextInput
                v-model="search"
                :placeholder="t('stationPages.editor.browseFilesSearch')"
            />
            <div class="flex-1 min-h-0 overflow-y-auto">
                <div v-if="loading" class="flex items-center justify-center py-8">
                    <Spinner size="md"/>
                </div>
                <p v-else-if="filtered.length === 0" class="text-sm text-(--text-muted) text-center py-8">
                    {{ t('stationPages.editor.browseFilesEmpty') }}
                </p>
                <div v-else class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-3">
                    <button
                        v-for="f in filtered" :key="f.id"
                        type="button"
                        class="group flex flex-col rounded-theme border border-(--border) hover:border-primary hover:bg-primary/5 transition-colors overflow-hidden text-left"
                        @click="pick(f)"
                    >
                        <div class="aspect-square w-full bg-(--bg-accent) flex items-center justify-center overflow-hidden">
                            <img
                                v-if="isImage(f)"
                                :src="urlFor(f)"
                                :alt="f.fileName"
                                loading="lazy"
                                class="w-full h-full object-cover"
                            />
                            <font-awesome-icon v-else :icon="['fas', 'file']" class="text-3xl text-(--text-muted)"/>
                        </div>
                        <div class="p-2 text-xs space-y-0.5 min-w-0">
                            <p class="truncate font-medium" :title="f.fileName">{{ f.fileName }}</p>
                            <p class="text-(--text-muted)">{{ formatSize(f.fileSize) }}</p>
                        </div>
                    </button>
                </div>
            </div>
        </div>
    </Modal>
</template>
