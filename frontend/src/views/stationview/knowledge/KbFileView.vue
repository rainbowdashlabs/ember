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
import TextInput from '@/components/input/text/TextInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import {useSession} from '@/composables/useSession'
import {knowledgeBase, federation} from '@/api'
import type {KbFile, KbTag, MarkdownHtmlResponse} from '@/api/knowledgeBase'
import {KbFileType} from '@/api/knowledgeBase'
import {getItem} from '@/api/storage'

const {t} = useI18n()
const router = useRouter()
const route = useRoute()
const {canManageKnowledge, loaded} = useSession()

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
const newTagInput = ref('')
const showTagSuggestions = ref(false)

// Related files
const relatedFiles = ref<KbFile[]>([])
const showAddRelated = ref(false)
const allStationFiles = ref<KbFile[]>([])
const relatedSearchQuery = ref('')

// Description editing
const editingDescription = ref(false)
const editDescriptionValue = ref('')

const fileId = computed(() => Number(route.params.id))
const isFederated = computed(() => {
    if (!file.value) return false
    const stationId = Number(getItem('station_id'))
    return file.value.stationId !== stationId
})

async function copyToStation() {
    if (!file.value) return
    try {
        await federation.copyKbFile(file.value.id)
        // Navigate to the browse view after copying
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

function hideTagSuggestions() {
    setTimeout(() => { showTagSuggestions.value = false }, 200)
}

const tagSuggestions = computed(() => {
    const input = newTagInput.value.toLowerCase().trim()
    if (!input) return []
    const existing = new Set(fileTags.value.map(t => t.name.toLowerCase()))
    return allStationTags.value
        .filter(t => t.name.toLowerCase().includes(input) && !existing.has(t.name.toLowerCase()))
        .slice(0, 8)
})

async function addTag(name?: string) {
    const tagName = (name ?? newTagInput.value).trim()
    if (!file.value || !tagName) return
    const tagNames = fileTags.value.map(t => t.name)
    tagNames.push(tagName)
    fileTags.value = await knowledgeBase.setFileTags(file.value.id, tagNames)
    allStationTags.value = await knowledgeBase.listTags()
    newTagInput.value = ''
    showTagSuggestions.value = false
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

async function openAddRelated() {
    showAddRelated.value = true
    relatedSearchQuery.value = ''
    // Load all files from the same station for picking
    if (file.value) {
        const browse = await knowledgeBase.browse()
        allStationFiles.value = browse.files.filter(f => f.id !== file.value!.id)
    }
}

const filteredRelatedCandidates = computed(() => {
    const existingIds = new Set(relatedFiles.value.map(f => f.id))
    const q = relatedSearchQuery.value.toLowerCase()
    return allStationFiles.value
        .filter(f => !existingIds.has(f.id))
        .filter(f => !q || f.name.toLowerCase().includes(q) || f.description.toLowerCase().includes(q))
        .slice(0, 10)
})

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
            <div class="flex flex-wrap items-center gap-3 mb-4">
                <SecondaryButton @click="goBack">
                    <font-awesome-icon :icon="['fas', 'chevron-left']"/>
                    {{ t('kb.backToBrowse') }}
                </SecondaryButton>

                <h1 class="text-xl font-bold flex-1">{{ file.name }}</h1>

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
            <div v-if="!isFederated && editingDescription" class="flex items-center gap-2 mb-4">
                <TextAreaInput v-model="editDescriptionValue" class="flex-1 !text-sm" :placeholder="t('kb.description')"/>
                <PrimaryButton @click="saveDescription">
                    <font-awesome-icon :icon="['fas', 'check']"/>
                </PrimaryButton>
                <SecondaryButton @click="editingDescription = false">
                    <font-awesome-icon :icon="['fas', 'xmark']"/>
                </SecondaryButton>
            </div>
            <p v-else-if="file.description || (!isFederated && canManageKnowledge())" class="text-sm text-[var(--text-muted)] mb-4 group/desc">
                {{ file.description || t('kb.description') }}
                <button
                    v-if="!isFederated && canManageKnowledge()"
                    class="opacity-0 group-hover/desc:opacity-100 ml-1 text-[var(--primary)] transition-opacity cursor-pointer"
                    @click="startEditDescription"
                >
                    <font-awesome-icon :icon="['fas', 'pen']" class="text-xs"/>
                </button>
            </p>

            <!-- Last edit info -->
            <p v-if="file.updatedAt" class="text-xs text-[var(--text-muted)] mb-3">
                {{ t('kb.lastEditedAt') }}: {{ new Date(file.updatedAt).toLocaleString('de-DE') }}
                <span v-if="lastEditedByName"> &mdash; {{ lastEditedByName }}</span>
            </p>

            <!-- Tags -->
            <div v-if="fileTags.length > 0 || canManageKnowledge()" class="flex flex-wrap items-center gap-2 mb-4">
                <font-awesome-icon :icon="['fas', 'tags']" class="text-xs text-[var(--text-muted)]"/>
                <span
                    v-for="tag in fileTags"
                    :key="tag.id"
                    class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs bg-[var(--bg-accent)] text-[var(--text)]"
                >
                    {{ tag.name }}
                    <button
                        v-if="canManageKnowledge()"
                        type="button"
                        class="hover:text-[var(--error)] transition-colors cursor-pointer"
                        @click="removeTag(tag.name)"
                    >
                        <font-awesome-icon :icon="['fas', 'xmark']" class="text-[10px]"/>
                    </button>
                </span>
                <div v-if="canManageKnowledge()" class="relative inline-flex">
                    <form @submit.prevent="addTag()">
                        <TextInput
                            v-model="newTagInput"
                            :placeholder="t('kb.tagPlaceholder')"
                            class="!py-0.5 !px-2 !text-xs w-32"
                            @focus="showTagSuggestions = true"
                            @blur="hideTagSuggestions"
                        />
                    </form>
                    <div
                        v-if="showTagSuggestions && tagSuggestions.length > 0"
                        class="absolute top-full left-0 mt-1 z-20 min-w-40 rounded border border-[var(--border)] bg-[var(--bg)] shadow-lg py-1"
                    >
                        <button
                            v-for="suggestion in tagSuggestions"
                            :key="suggestion.id"
                            type="button"
                            class="w-full text-left px-3 py-1 text-xs hover:bg-[var(--bg-accent)] transition-colors cursor-pointer"
                            @mousedown.prevent="addTag(suggestion.name)"
                        >
                            {{ suggestion.name }}
                        </button>
                    </div>
                </div>
            </div>

            <!-- Related files / Further reading -->
            <div v-if="relatedFiles.length > 0 || canManageKnowledge()" class="mb-4">
                <div class="flex items-center gap-2 mb-2">
                    <font-awesome-icon :icon="['fas', 'book-open']" class="text-xs text-[var(--text-muted)]"/>
                    <span class="text-sm font-medium">{{ t('kb.relatedFiles') }}</span>
                    <button
                        v-if="canManageKnowledge()"
                        type="button"
                        class="text-[var(--primary)] text-xs hover:underline cursor-pointer"
                        @click="openAddRelated"
                    >
                        <font-awesome-icon :icon="['fas', 'plus']" /> {{ t('common.add') }}
                    </button>
                </div>
                <div class="flex flex-wrap gap-2">
                    <router-link
                        v-for="rf in relatedFiles"
                        :key="rf.id"
                        :to="{name: 'kb-file', params: {id: rf.id}}"
                        class="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg text-sm bg-[var(--bg-accent)] border border-[var(--border)] hover:border-[var(--primary)] transition-colors group/rf"
                    >
                        <font-awesome-icon :icon="['fas', 'file-lines']" class="text-xs text-[var(--primary)]"/>
                        {{ rf.name }}
                        <button
                            v-if="canManageKnowledge()"
                            type="button"
                            class="opacity-0 group-hover/rf:opacity-100 text-[var(--error)] transition-opacity cursor-pointer"
                            @click.prevent="removeRelatedFile(rf.id)"
                        >
                            <font-awesome-icon :icon="['fas', 'xmark']" class="text-[10px]"/>
                        </button>
                    </router-link>
                </div>
                <!-- Add related file picker -->
                <div v-if="showAddRelated" class="mt-2 p-3 rounded-lg border border-[var(--border)] bg-[var(--bg)]">
                    <TextInput
                        v-model="relatedSearchQuery"
                        :placeholder="t('kb.searchRelated')"
                        class="!text-sm mb-2"
                    />
                    <div v-if="filteredRelatedCandidates.length > 0" class="flex flex-col gap-1 max-h-40 overflow-y-auto">
                        <button
                            v-for="candidate in filteredRelatedCandidates"
                            :key="candidate.id"
                            type="button"
                            class="flex items-center gap-2 px-2 py-1 text-sm rounded hover:bg-[var(--bg-accent)] text-left transition-colors cursor-pointer"
                            @click="addRelatedFile(candidate.id)"
                        >
                            <font-awesome-icon :icon="['fas', 'file-lines']" class="text-xs text-[var(--primary)]"/>
                            <span class="truncate">{{ candidate.name }}</span>
                            <span v-if="candidate.description" class="text-xs text-[var(--text-muted)] truncate">{{ candidate.description }}</span>
                        </button>
                    </div>
                    <p v-else class="text-xs text-[var(--text-muted)]">{{ t('kb.noFilesFound') }}</p>
                    <SecondaryButton class="mt-2 !text-xs !py-1 !px-2" @click="showAddRelated = false">
                        {{ t('common.close') }}
                    </SecondaryButton>
                </div>
            </div>

            <!-- Save bar -->
            <div v-if="editing" class="flex items-center gap-3 mb-3">
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
