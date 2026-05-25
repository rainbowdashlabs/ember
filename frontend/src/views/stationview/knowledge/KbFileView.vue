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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import IconButton from '@/components/button/IconButton.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import KbTagsSection from '@/views/stationview/knowledge/kbfileview/KbTagsSection.vue'
import KbRelatedFilesSection from '@/views/stationview/knowledge/kbfileview/KbRelatedFilesSection.vue'
import {useSession} from '@/composables/useSession'
import {knowledgeBase} from '@/api'
import type {KbFile, KbTag, MarkdownHtmlResponse} from '@/api/knowledgeBase'
import {KbFileType} from '@/api/knowledgeBase'
import {getItem} from '@/api/storage'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
const router = useRouter()
const route = useRoute()
const {canManageKnowledge, loaded, isKbPublic} = useSession()

const file = ref<KbFile | null>(null)
const lastEditedByName = ref<string | null>(null)
const loading = ref(true)
const error = ref('')

// Markdown
const markdownData = ref<MarkdownHtmlResponse | null>(null)
const editing = ref(false)
const editContent = ref('')
const saving = ref(false)

// Text
const textContent = ref('')

// Tags
const fileTags = ref<KbTag[]>([])
const allStationTags = ref<KbTag[]>([])

// Related files
const relatedFiles = ref<KbFile[]>([])

// Description editing
const editingDescription = ref(false)
const editDescriptionValue = ref('')

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
    saving.value = true
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
    } catch {
        error.value = t('common.error')
    } finally {
        saving.value = false
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
    const url = `${window.location.origin}/public/kb/${stationUid}/file/${file.value.id}`
    navigator.clipboard.writeText(url).then(() => {
        shareCopied.value = true
        setTimeout(() => { shareCopied.value = false }, 2000)
    })
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
                <template v-else-if="canManageKnowledge()">
                    <PrimaryButton
                        v-if="file.fileType === KbFileType.MARKDOWN || file.fileType === KbFileType.TEXT"
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
            <MutedText tag="p" size="sm" v-else-if="file.description || canManageKnowledge()" class="group/desc">
                {{ file.description || t('kb.description') }}
                <IconButton
                    v-if="canManageKnowledge()"
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
                :can-manage="canManageKnowledge()"
                @add-tag="addTag"
                @remove-tag="removeTag"
            />

            <!-- Related files (hide for federated files) -->
            <KbRelatedFilesSection
                v-if="!isFederated"
                :related-files="relatedFiles"
                :file-id="file.id"
                :can-manage="canManageKnowledge()"
                @add-related="addRelatedFile"
                @remove-related="removeRelatedFile"
            />

            <!-- Save bar -->
            <div v-if="editing" class="flex items-center gap-2 mb-3">
                <PrimaryButton :disabled="saving || !hasUnsavedChanges" @click="saveContent">
                    <font-awesome-icon :icon="['fas', saving ? 'spinner' : 'check']" :spin="saving"/>
                    {{ t('kb.save') }}
                </PrimaryButton>
                <span v-if="hasUnsavedChanges" class="text-sm text-[var(--text-muted)]">
                    {{ t('kb.unsavedChanges') }}
                </span>
            </div>

            <!-- MARKDOWN -->
            <template v-if="file.fileType === KbFileType.MARKDOWN">
                <MarkdownEditor
                    v-if="editing"
                    v-model="editContent"
                    :file-id="file?.id"
                    @update:model-value="onContentInput"
                />
                <NeutralContainer v-else>
                    <div v-if="renderedHtml" class="markdown-content"
                         v-html="renderedHtml"/>
                    <p v-else class="text-[var(--text-muted)]">{{ t('kb.noContent') }}</p>
                </NeutralContainer>
            </template>

            <!-- TEXT -->
            <template v-else-if="file.fileType === KbFileType.TEXT">
                <div v-if="editing">
                    <TextAreaInput
                        v-model="editContent"
                        class="font-mono min-h-[400px]"
                        @input="onContentInput"
                    />
                </div>
                <NeutralContainer v-else>
                    <pre v-if="textContent" class="whitespace-pre-wrap text-sm">{{ textContent }}</pre>
                    <p v-else class="text-[var(--text-muted)]">{{ t('kb.noContent') }}</p>
                </NeutralContainer>
            </template>

            <!-- PDF -->
            <template v-else-if="file.fileType === KbFileType.PDF">
                <NeutralContainer class="p-0">
                    <iframe
                        :src="contentUrl"
                        class="w-full min-h-[80vh] rounded"
                        :title="file.name"
                    />
                </NeutralContainer>
            </template>

            <!-- IMAGE -->
            <template v-else-if="file.fileType === KbFileType.IMAGE">
                <NeutralContainer class="flex justify-center">
                    <img
                        :src="contentUrl"
                        :alt="file.name"
                        class="max-w-full max-h-[80vh] rounded"
                    />
                </NeutralContainer>
            </template>

            <!-- YOUTUBE -->
            <template v-else-if="file.fileType === KbFileType.YOUTUBE">
                <NeutralContainer v-if="youtubeEmbedUrl" class="p-0">
                    <div class="relative pb-[56.25%] h-0">
                        <iframe
                            :src="youtubeEmbedUrl"
                            class="absolute top-0 left-0 w-full h-full rounded"
                            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                            allowfullscreen
                            :title="file.name"
                        />
                    </div>
                </NeutralContainer>
                <Alert v-else variant="error">{{ t('kb.noContent') }}</Alert>
            </template>

            <!-- LINK -->
            <template v-else-if="file.fileType === KbFileType.LINK">
                <NeutralContainer class="space-y-4">
                    <div class="flex items-center gap-2">
                        <font-awesome-icon :icon="['fas', 'link']" class="text-[var(--secondary)]"/>
                        <a
                            :href="file.linkUrl ?? ''"
                            target="_blank"
                            rel="noopener noreferrer"
                            class="text-[var(--primary)] hover:underline break-all"
                        >
                            {{ file.linkUrl }}
                        </a>
                    </div>
                    <a :href="file.linkUrl ?? ''" target="_blank" rel="noopener noreferrer" class="inline-block">
                        <PrimaryButton>
                            <font-awesome-icon :icon="['fas', 'arrow-right']"/>
                            {{ t('kb.openLink') }}
                        </PrimaryButton>
                    </a>
                </NeutralContainer>
            </template>

            <!-- OTHER -->
            <template v-else>
                <NeutralContainer class="text-center py-8">
                    <font-awesome-icon :icon="['fas', 'file']" class="text-4xl text-[var(--text-muted)] mb-4"/>
                    <p class="mb-4">{{ file.name }}</p>
                    <a :href="contentUrl" download class="inline-block">
                        <PrimaryButton>
                            <font-awesome-icon :icon="['fas', 'download']"/>
                            {{ t('kb.download') }}
                        </PrimaryButton>
                    </a>
                </NeutralContainer>
            </template>
        </template>
    </ViewContent>
</template>
