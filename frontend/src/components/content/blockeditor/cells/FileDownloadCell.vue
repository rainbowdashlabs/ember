/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {type FileDownloadConfig} from '@/api/pageManage'
import {listMediaFiles, type StationFile} from '@/api/media'

const props = defineProps<{
    config: FileDownloadConfig
}>()

const fileResolved = ref<StationFile | null>(null)

async function resolveFileDownload() {
    fileResolved.value = null
    const url = props.config.url ?? ''
    const m = url.match(/\/files\/([0-9a-f]{64})$/)
    if (!m) return
    const hash = m[1]
    try {
        const listing = await listMediaFiles()
        fileResolved.value = listing.find(l => l.file.contentHash === hash)?.file ?? null
    } catch { fileResolved.value = null }
}

function formatFileBytes(bytes: number): string {
    if (bytes >= 1024 * 1024) {
        const mb = bytes / (1024 * 1024)
        return `${mb % 1 === 0 ? mb.toFixed(0) : mb.toFixed(1)} MB`
    }
    if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
    return `${bytes} B`
}

onMounted(resolveFileDownload)
watch(() => props.config.url, resolveFileDownload)
</script>

<template>
    <a v-if="config.url" :href="config.url" target="_blank" rel="noopener noreferrer"
       class="flex items-center gap-3 rounded-theme border border-(--border) hover:border-primary hover:bg-primary/5 transition-colors px-4 py-3" download>
        <font-awesome-icon :icon="['fas', 'file']" class="text-2xl text-primary shrink-0"/>
        <div class="flex-1 min-w-0">
            <p class="font-medium truncate">{{ config.label || fileResolved?.fileName || config.url.split('/').pop() || 'Datei' }}</p>
            <MutedText v-if="fileResolved || config.description" tag="p" class="truncate">
                <span v-if="fileResolved">{{ formatFileBytes(fileResolved.fileSize) }}</span>
                <span v-if="fileResolved && config.description" class="mx-1">·</span>
                <span v-if="config.description">{{ config.description }}</span>
            </MutedText>
        </div>
        <font-awesome-icon :icon="['fas', 'download']" class="text-(--text-muted)"/>
    </a>
    <EmptyHint v-else>Datei-URL fehlt</EmptyHint>
</template>
