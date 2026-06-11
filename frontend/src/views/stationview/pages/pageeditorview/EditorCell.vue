/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {marked} from 'marked'
import SelectInput from '@/components/input/select/SelectInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CellMarkdownEditor from './CellMarkdownEditor.vue'
import CellImageEditor from './CellImageEditor.vue'
import CellVideoEditor from './CellVideoEditor.vue'
import {
    CellContentType,
    pageImageUrl,
    type CellContentTypeName,
    type ImageConfig,
    type VideoConfig,
} from '@/api/pageManage'
import {usePageClipboard} from '@/composables/usePageClipboard'

export interface CellEditData {
    id: number
    sortOrder: number
    widthPercent: number
    contentType: CellContentTypeName
    content: string
    config: Record<string, unknown>
}

const props = defineProps<{
    cell: CellEditData
    pageId: number
    stationUid: string
    preview: boolean
}>()

const emit = defineEmits<{
    'update:cell': [cell: CellEditData]
    delete: []
}>()

const {t} = useI18n()
const {copyCell, cutCell} = usePageClipboard()

const imageConfig = computed<ImageConfig>(() => (props.cell.config as ImageConfig) ?? {})
const videoConfig = computed<VideoConfig>(() => (props.cell.config as VideoConfig) ?? {})

function updateField<K extends keyof CellEditData>(key: K, value: CellEditData[K]) {
    emit('update:cell', {...props.cell, [key]: value})
}

function onContentTypeChange(type: string) {
    emit('update:cell', {
        ...props.cell,
        contentType: type as CellContentTypeName,
        content: '',
        config: {},
    })
}

function onCopy() {
    copyCell(props.cell)
}

function onCut() {
    cutCell(props.cell, () => emit('delete'))
}

const imageUrl = computed(() => {
    if (props.cell.contentType !== CellContentType.IMAGE || !props.cell.content) return ''
    return pageImageUrl(props.stationUid, Number(props.cell.content))
})

const renderedHtml = computed(() => {
    if (props.cell.contentType !== CellContentType.MARKDOWN || !props.cell.content) return ''
    return marked.parse(props.cell.content) as string
})

const isYouTube = computed(() => {
    if (props.cell.contentType !== CellContentType.VIDEO) return false
    return /youtube\.com|youtu\.be/.test(props.cell.content)
})

const youtubeEmbedUrl = computed(() => {
    const url = props.cell.content
    const match = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/)([a-zA-Z0-9_-]{11})/)
    if (match) return `https://www.youtube-nocookie.com/embed/${match[1]}`
    return url
})

</script>

<template>
    <!-- Preview mode -->
    <div v-if="preview" class="w-full">
        <!-- Markdown preview -->
        <div
            v-if="cell.contentType === CellContentType.MARKDOWN && cell.content"
            class="markdown-content"
        >
            <div v-html="renderedHtml"/>
        </div>

        <!-- Image preview -->
        <div v-else-if="cell.contentType === CellContentType.IMAGE && imageUrl" class="w-full">
            <img
                :src="imageUrl"
                :alt="(imageConfig.altText as string) ?? ''"
                :style="{
                    objectFit: (imageConfig.imageFit as string)?.toLowerCase() ?? 'contain',
                    maxHeight: imageConfig.maxHeight ? `${imageConfig.maxHeight}px` : undefined,
                }"
                class="w-full rounded-theme"
            />
        </div>

        <!-- Video preview -->
        <div v-else-if="cell.contentType === CellContentType.VIDEO && cell.content" class="w-full">
            <div v-if="isYouTube" class="relative w-full" style="padding-bottom: 56.25%">
                <iframe
                    :src="youtubeEmbedUrl"
                    class="absolute inset-0 w-full h-full rounded-theme"
                    frameborder="0"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                    allowfullscreen
                />
            </div>
            <video
                v-else
                :src="cell.content"
                :autoplay="!!videoConfig.autoplay"
                :loop="!!videoConfig.loop"
                controls
                class="w-full rounded-theme"
            />
        </div>
    </div>

    <!-- Edit mode -->
    <NeutralContainer v-else class="w-full flex-1 flex flex-col space-y-3">
        <!-- Toolbar -->
        <div class="flex items-center justify-between gap-2">
            <SelectInput :model-value="cell.contentType" @update:model-value="onContentTypeChange">
                <option :value="CellContentType.MARKDOWN">{{ t('stationPages.contentType.markdown') }}</option>
                <option :value="CellContentType.IMAGE">{{ t('stationPages.contentType.image') }}</option>
                <option :value="CellContentType.VIDEO">{{ t('stationPages.contentType.video') }}</option>
            </SelectInput>
            <div class="flex items-center gap-1">
                <IconButton
                    :icon="['fas', 'copy']"
                    :label="t('stationPages.editor.copyCell')"
                    class="text-[var(--text-muted)] hover:text-[var(--text)]"
                    @click="onCopy"
                />
                <IconButton
                    :icon="['fas', 'scissors']"
                    :label="t('stationPages.editor.cutCell')"
                    class="text-[var(--text-muted)] hover:text-[var(--text)]"
                    @click="onCut"
                />
                <DeleteButton @click="$emit('delete')"/>
            </div>
        </div>

        <!-- Markdown editor -->
        <CellMarkdownEditor
            v-if="cell.contentType === CellContentType.MARKDOWN"
            class="flex-1 flex flex-col"
            :content="cell.content"
            @update:content="updateField('content', $event)"
        />

        <!-- Image editor -->
        <CellImageEditor
            v-else-if="cell.contentType === CellContentType.IMAGE"
            :content="cell.content"
            :config="cell.config as Record<string, unknown>"
            :page-id="pageId"
            :station-uid="stationUid"
            @update:content="updateField('content', $event)"
            @update:config="updateField('config', $event)"
        />

        <!-- Video editor -->
        <CellVideoEditor
            v-else-if="cell.contentType === CellContentType.VIDEO"
            :content="cell.content"
            :config="cell.config as Record<string, unknown>"
            @update:content="updateField('content', $event)"
            @update:config="updateField('config', $event)"
        />
    </NeutralContainer>
</template>
