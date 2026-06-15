/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, onMounted, watch, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import KbTagsSection from '@/views/stationview/knowledge/kbfileview/KbTagsSection.vue'
import KbRelatedFilesSection from '@/views/stationview/knowledge/kbfileview/KbRelatedFilesSection.vue'
import KbCommentSection from '@/components/comment/KbCommentSection.vue'
import PresentationViewer from '@/views/stationview/knowledge/kbfileview/PresentationViewer.vue'
import KbFileContent from '@/views/stationview/knowledge/kbfileview/KbFileContent.vue'
import KbEditFileModal from '@/views/stationview/knowledge/knowledgebaseview/KbEditFileModal.vue'
import {useSession} from '@/composables/useSession'
import {knowledgeBase} from '@/api'
import type {KbFile, KbTag, MarkdownHtmlResponse} from '@/api/knowledgeBase'
import {KbFileType} from '@/api/knowledgeBase'
import {getItem} from '@/api/storage'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
const router = useRouter()
const route = useRoute()
const {canEditKnowledge, loaded, isKbPublic} = useSession()

const file = ref<KbFile | null>(null)
const lastEditedByName = ref<string | null>(null)
const loading = ref(true)
const error = ref('')
const markdownData = ref<MarkdownHtmlResponse | null>(null)
const editing = ref(false)
const editContent = ref('')
const textContent = ref('')
const fileTags = ref<KbTag[]>([])
const allStationTags = ref<KbTag[]>([])
const relatedFiles = ref<KbFile[]>([])
const editingDescription = ref(false)
const editDescriptionValue = ref('')
const showPresentation = ref(false)
const showEditMetadataModal = ref(false)
const originalUrl = computed(() => file.value ? knowledgeBase.originalFileUrl(file.value.id) : '')

const fileId = computed(() => Number(route.params.id))
const isFederated = computed(() => {
    if (!file.value) return false
    const myStationId = getItem('station_id')
    return file.value.stationId !== myStationId
})

async function copyToStation() {
    if (!file.value) return
    try {
        const {federation} = await import('@/api')
        await federation.copyKbFile(file.value.id)
        router.push({name: 'kb-browse'})
    } catch {
        error.value = t('common.error')
    }
}

// Process rendered HTML: add auth tokens to KB image URLs (leaves external URLs untouched)
const renderedHtml = computed(() => {
    if (!markdownData.value?.html) return ''
    const token = getItem('session_token') ?? ''
    const stationId = getItem('station_id') ?? ''
    return markdownData.value.html.replace(
        /src="([^"]*\/kb\/images\/[^"]*)"/g,
        (_match, url) => {
            try {
                const parsed = new URL(url, window.location.origin)
                if (!parsed.searchParams.has('token')) {
                    parsed.searchParams.set('token', token)
                    parsed.searchParams.set('stationId', stationId)
                }
                return `src="${parsed.toString()}"`
            } catch {
                return `src="${url}"`
            }
        }
    )
})

function extractYoutubeId(url: string): string | null {
    const patterns = [
        /(?:youtube\.com\/watch\?v=)([a-zA-Z0-9_-]{11})/,
        /(?:youtu\.be\/)([a-zA-Z0-9_-]{11})/,
        /(?:youtube\.com\/embed\/)([a-zA-Z0-9_-]{11})/,
    ]
    for (const pattern of patterns) {
        const match = url.match(pattern)
        if (match) return match[1]
    }
    return null
}

const youtubeEmbedUrl = computed(() => {
    if (!file.value?.youtubeUrl) return null
    const id = extractYoutubeId(file.value.youtubeUrl)
    return id ? `https://www.youtube-nocookie.com/embed/${id}` : null
})

const contentUrl = computed(() => {
    if (!file.value) return ''
    return knowledgeBase.fileContentUrl(file.value.id)
})

async function loadData() {
    loading.value = true
    error.value = ''
    try {
        const fileRes = await knowledgeBase.getFile(fileId.value)
        file.value = fileRes.file
        lastEditedByName.value = fileRes.lastEditedByName

        const [tags, stationTags, related] = await Promise.all([
            knowledgeBase.getFileTags(file.value.id),
            knowledgeBase.listTags(),
            knowledgeBase.getRelatedFiles(file.value.id),
        ])
        fileTags.value = tags
        allStationTags.value = stationTags
        relatedFiles.value = related

        if (file.value.fileType === KbFileType.MARKDOWN) {
            markdownData.value = await knowledgeBase.getMarkdownHtml(file.value.id)
            editContent.value = markdownData.value.markdown
        } else if (file.value.fileType === KbFileType.TEXT) {
            textContent.value = await knowledgeBase.getTextContent(file.value.id)
            editContent.value = textContent.value
        }
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
}
function toggleEdit() {
    editing.value = !editing.value
    if (editing.value) {
        if (file.value?.fileType === KbFileType.MARKDOWN && markdownData.value) {
            editContent.value = markdownData.value.markdown
        } else if (file.value?.fileType === KbFileType.TEXT) {
            editContent.value = textContent.value
        }
    }
}

const hasUnsavedChanges = ref(false)
function onContentInput() {
    hasUnsavedChanges.value = true
}

async function saveContent() {
    if (!file.value) return
    try {
        await knowledgeBase.updateMarkdownContent(file.value.id, editContent.value)
        // Refresh rendered HTML
        if (file.value.fileType === KbFileType.MARKDOWN) {
            markdownData.value = await knowledgeBase.getMarkdownHtml(file.value.id)
        } else {
            textContent.value = editContent.value
        }
        hasUnsavedChanges.value = false
        editing.value = false
    } catch (e) {
        error.value = t('common.error')
        throw e
    }
}
async function addTag(name: string) {
    if (!file.value) return
    const tagNames = fileTags.value.map(t => t.name)
    tagNames.push(name)
    fileTags.value = await knowledgeBase.setFileTags(file.value.id, tagNames)
    allStationTags.value = await knowledgeBase.listTags()
}
async function removeTag(tagName: string) {
    if (!file.value) return
    const tagNames = fileTags.value.map(t => t.name).filter(n => n !== tagName)
    fileTags.value = await knowledgeBase.setFileTags(file.value.id, tagNames)
}
function startEditDescription() {
    editingDescription.value = true
    editDescriptionValue.value = file.value?.description ?? ''
}

async function saveDescription() {
    if (!file.value) return
    await knowledgeBase.updateFile(file.value.id, {
        name: file.value.name,
        description: editDescriptionValue.value,
    })
    const reloaded = await knowledgeBase.getFile(file.value.id)
    file.value = reloaded.file
    lastEditedByName.value = reloaded.lastEditedByName
    editingDescription.value = false
}
async function addRelatedFile(targetId: number) {
    if (!file.value) return
    const ids = [...relatedFiles.value.map(f => f.id), targetId]
    relatedFiles.value = await knowledgeBase.setRelatedFiles(file.value.id, ids)
}

async function removeRelatedFile(targetId: number) {
    if (!file.value) return
    const ids = relatedFiles.value.map(f => f.id).filter(id => id !== targetId)
    relatedFiles.value = await knowledgeBase.setRelatedFiles(file.value.id, ids)
}
function goBack() {
    if (file.value?.folderId) {
        router.push({name: 'kb-browse', query: {folderId: file.value.folderId}})
    } else {
        router.push({name: 'kb-browse'})
    }
}
const shareCopied = ref(false)
function copyShareLink() {
    if (!file.value) return
    const stationUid = getItem('station_id') ?? ''
    const url = `${window.location.origin}/public/station/${stationUid}/knowledge/file/${file.value.id}`
    navigator.clipboard.writeText(url).then(() => {
        shareCopied.value = true
        setTimeout(() => { shareCopied.value = false }, 2000)
    })
}
async function handleReuploadFile(uploadFile: File) {
    if (!file.value) return
    try {
        file.value = await knowledgeBase.reuploadOriginal(file.value.id, uploadFile)
    } catch {
        error.value = t('common.error')
    }
}
watch(loaded, (isLoaded) => {
    if (isLoaded) loadData()
}, {immediate: true})
onMounted(() => {
    if (loaded.value) loadData()
})
</script>

<template>
    <ViewContent>
        <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>
        <Spinner v-if="loading"/>

        <template v-else-if="file">
            <!-- Header -->
            <div class="flex flex-wrap items-center gap-2 mb-4">
                <SecondaryButton @click="goBack">
                    <font-awesome-icon :icon="['fas', 'chevron-left']"/>
                    {{ t('kb.backToBrowse') }}
                </SecondaryButton>

                <PageHeader class="flex-1 !mb-0">{{ file.name }}</PageHeader>

                <IconButton
                    v-if="isKbPublic()"
                    :icon="['fas', shareCopied ? 'check' : 'share-nodes']"
                    :label="t('kb.shareLink')"
                    :class="shareCopied ? '!text-green-500' : '!text-[var(--text-muted)]'"
                    @click="copyShareLink"
                />

                <PrimaryButton v-if="isFederated" @click="copyToStation">
                    <font-awesome-icon :icon="['fas', 'copy']"/>
                    {{ t('federation.copyToStation') }}
                </PrimaryButton>
                <template v-else>
                    <SecondaryButton
                        v-if="canEditKnowledge()"
                        @click="showEditMetadataModal = true"
                    >
                        <font-awesome-icon :icon="['fas', 'gear']"/>
                        {{ t('kb.editMetadata') }}
                    </SecondaryButton>
                    <PrimaryButton
                        v-if="canEditKnowledge() && (file.fileType === KbFileType.MARKDOWN || file.fileType === KbFileType.TEXT)"
                        @click="toggleEdit"
                    >
                        <font-awesome-icon :icon="['fas', editing ? 'eye' : 'pen']"/>
                        {{ editing ? t('kb.preview') : t('kb.edit') }}
                    </PrimaryButton>
                    <SecondaryButton
                        v-if="file.fileType === KbFileType.MARKDOWN"
                        @click="router.push({name: 'kb-versions', params: {id: file.id}})"
                    >
                        <font-awesome-icon :icon="['fas', 'clock-rotate-left']"/>
                        {{ t('kb.versions') }}
                    </SecondaryButton>
                    <SecondaryButton
                        v-if="(file.fileType === KbFileType.PDF || (file.fileType === KbFileType.PRESENTATION && file.conversionStatus === 'SUCCESS'))"
                        @click="showPresentation = true"
                    >
                        <font-awesome-icon :icon="['fas', 'display']"/>
                        {{ t('kb.present') }}
                    </SecondaryButton>
                    <a v-if="file.fileType === KbFileType.PRESENTATION" :href="originalUrl" download class="inline-block">
                        <SecondaryButton>
                            <font-awesome-icon :icon="['fas', 'download']"/>
                            {{ t('kb.downloadOriginal') }}
                        </SecondaryButton>
                    </a>
                </template>
            </div>

            <!-- Description -->
            <div v-if="editingDescription" class="flex items-center gap-2 mb-4">
                <TextAreaInput v-model="editDescriptionValue" class="flex-1 !text-sm" :placeholder="t('kb.description')"/>
                <PrimaryButton @click="saveDescription">
                    <font-awesome-icon :icon="['fas', 'check']"/>
                </PrimaryButton>
                <SecondaryButton @click="editingDescription = false">
                    <font-awesome-icon :icon="['fas', 'xmark']"/>
                </SecondaryButton>
            </div>
            <MutedText tag="p" size="sm" v-else-if="file.description || canEditKnowledge()" class="group/desc">
                {{ file.description || t('kb.description') }}
                <IconButton
                    v-if="canEditKnowledge()"
                    :icon="['fas', 'pen']"
                    :label="t('kb.edit')"
                    class="opacity-0 group-hover/desc:opacity-100 ml-1 text-[var(--primary)] !p-0 text-xs"
                    @click="startEditDescription"
                />
            </MutedText>

            <!-- Last edit info -->
            <p v-if="file.updatedAt" class="text-xs text-[var(--text-muted)] mb-3">
                {{ t('kb.lastEditedAt') }}: {{ new Date(file.updatedAt).toLocaleString('de-DE') }}
                <span v-if="lastEditedByName"> &mdash; {{ lastEditedByName }}</span>
            </p>

            <!-- Tags (hide for federated files) -->
            <KbTagsSection
                v-if="!isFederated"
                :tags="fileTags"
                :all-tags="allStationTags"
                :can-manage="canEditKnowledge()"
                @add-tag="addTag"
                @remove-tag="removeTag"
            />

            <!-- Related files (hide for federated files) -->
            <KbRelatedFilesSection
                v-if="!isFederated"
                :related-files="relatedFiles"
                :file-id="file.id"
                :can-manage="canEditKnowledge()"
                @add-related="addRelatedFile"
                @remove-related="removeRelatedFile"
            />

            <!-- Save bar -->
            <div v-if="editing" class="flex items-center gap-2 mb-3">
                <SaveButton :disabled="!hasUnsavedChanges" :action="saveContent"/>
                <span v-if="hasUnsavedChanges" class="text-sm text-[var(--text-muted)]">
                    {{ t('kb.unsavedChanges') }}
                </span>
            </div>

            <KbFileContent
                :file="file"
                :editing="editing"
                :content-url="contentUrl"
                :text-content="textContent"
                :rendered-html="renderedHtml"
                :youtube-embed-url="youtubeEmbedUrl"
                :can-edit="canEditKnowledge()"
                v-model:edit-content="editContent"
                @content-input="onContentInput"
                @reupload="handleReuploadFile"
            />

            <!-- Comments -->
            <KbCommentSection
                :file-id="file.id"
                :station-uid="isFederated ? file.stationId : undefined"
                class="mt-6"
            />
        </template>
        <!-- Presentation Viewer Overlay -->
        <PresentationViewer
            v-if="showPresentation && file"
            :content-url="contentUrl"
            :title="file.name"
            @close="showPresentation = false"
        />

        <!-- Edit metadata modal (name / description / visibility / restrictions / tags) -->
        <KbEditFileModal
            :show="showEditMetadataModal"
            :file="file"
            @update:show="showEditMetadataModal = $event"
            @saved="loadData()"
        />
    </ViewContent>
</template>
