/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch, computed} from 'vue'
import {renderMarkdown} from '@/util/markdown'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import KbFileHeaderBar from '@/views/stationview/knowledge/kbfileview/KbFileHeaderBar.vue'
import KbTagsSection from '@/views/stationview/knowledge/kbfileview/KbTagsSection.vue'
import KbRelatedFilesSection from '@/views/stationview/knowledge/kbfileview/KbRelatedFilesSection.vue'
import KbCommentSection from '@/components/comment/KbCommentSection.vue'
import PresentationViewer from '@/views/stationview/knowledge/kbfileview/PresentationViewer.vue'
import KbFileContent from '@/views/stationview/knowledge/kbfileview/KbFileContent.vue'
import {ContentMode, type ContentModeName} from '@/api/news'
import type {RowEditData} from '@/components/content/blockeditor/EditorRow.vue'
import type {PageRow, SaveRowRequest, SaveCellRequest} from '@/api/pageManage'
import KbEditFileModal from '@/views/stationview/knowledge/knowledgebaseview/KbEditFileModal.vue'
import KbShareModal from '@/views/stationview/knowledge/knowledgebaseview/KbShareModal.vue'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {knowledgeBase} from '@/api'
import {
    KbAccessLevel,
    KbFileType,
    levelCovers,
    type KbAccessLevelName,
    type KbFile,
    type MarkdownHtmlResponse,
} from '@/api/knowledgeBase'
import {useKbFileMetadata} from '@/views/stationview/knowledge/kbfileview/useKbFileMetadata'
import {STATION_KB_ROUTES, type KbRoutes} from '@/views/stationview/knowledge/knowledgebaseview/useKbNavigation'
import {getItem} from '@/api/storage'
import {downloadAuthed} from '@/util/downloadAuthed'
import {formatDateTime} from '@/util/format'
import {youtubeEmbedUrl as toYoutubeEmbedUrl} from '@/util/youtube'
import MutedText from '@/components/typography/MutedText.vue'
import {useFlashMessage} from '@/composables/useFlashMessage'

const props = defineProps<{
    fileId: number
    stationUid?: string
    /** The pages this knowledge base is mounted on, which differ when an association opens its own. */
    routes?: KbRoutes
}>()

const routes = computed(() => props.routes ?? STATION_KB_ROUTES)

const {t} = useI18n()
const router = useRouter()
const {canEditKnowledge, loaded, isKbPublic, sessionInfo} = useSession()

/**
 * Whose media the blocks of this article point at: the partner's for a federated file, our own
 * otherwise.
 */
const blockStationUid = computed(() => props.stationUid ?? sessionInfo.value?.stationId ?? '')

const file = ref<KbFile | null>(null)
const lastEditedByName = ref<string | null>(null)
const markdownData = ref<MarkdownHtmlResponse | null>(null)
const editing = ref(false)
const editContent = ref('')
const contentMode = ref<ContentModeName>(ContentMode.SIMPLE)
const blockRows = ref<RowEditData[]>([])
const textContent = ref('')
const {
    fileTags, allStationTags, relatedFiles,
    editingDescription, editDescriptionValue,
    addTag, removeTag, addRelatedFile, removeRelatedFile,
    startEditDescription, saveDescription,
} = useKbFileMetadata(file, lastEditedByName)

const showPresentation = ref(false)
const showEditMetadataModal = ref(false)
const showShareModal = ref(false)
async function downloadOriginal() {
    if (!file.value) return
    await downloadAuthed(knowledgeBase.originalFileUrl(file.value.id), file.value.name)
}

async function downloadPdf() {
    if (!file.value) return
    const url = props.stationUid
        ? knowledgeBase.federatedPdfExportUrl(props.stationUid, file.value.id)
        : knowledgeBase.pdfExportUrl(file.value.id)
    await downloadAuthed(url, `${file.value.name}.pdf`)
}

const isFederated = computed(() => props.stationUid != null)

const isTextual = computed(() =>
    file.value?.fileType === KbFileType.MARKDOWN || file.value?.fileType === KbFileType.TEXT)

/**
 * A partner publishes the file record and, for textual files, the raw body. Videos and links need
 * nothing beyond the record, so they render; stored formats such as PDFs and images have no
 * federated content route and can only be read after copying the file.
 */
const federatedContentUnavailable = computed(() => isFederated.value
    && !isTextual.value
    && file.value?.fileType !== KbFileType.YOUTUBE
    && file.value?.fileType !== KbFileType.LINK)

/**
 * What this reader may do here. A folder can hold the level below the station-wide right, so the
 * page has to ask the file rather than the session.
 */
const accessLevel = ref<KbAccessLevelName | undefined>(undefined)
const accessLevelSource = ref<string | null>(null)

const mayEdit = computed(() =>
    canEditKnowledge() && !isFederated.value && levelCovers(accessLevel.value, KbAccessLevel.WRITE))

/**
 * Why editing is unavailable, when a folder is the reason. Silent absence reads as a bug, so the
 * page names the folder that holds the file read-only.
 */
const readOnlyReason = computed(() => {
    if (isFederated.value || mayEdit.value || !canEditKnowledge()) return ''
    if (!accessLevelSource.value) return t('kb.readOnlyHere')
    return t('kb.readOnlyFrom', {folder: accessLevelSource.value})
})

const canEditDescription = computed(() => mayEdit.value)

async function copyToStation() {
    if (!file.value) return
    try {
        const {federation} = await import('@/api')
        await federation.copyKbFile(file.value.id)
        router.push({name: routes.value.browse})
    } catch {
        error.value = t('common.error')
    }
}

const renderedHtml = computed(() => markdownData.value?.html ?? '')

const youtubeEmbedUrl = computed(() => {
    if (!file.value?.youtubeUrl) return ''
    return toYoutubeEmbedUrl(file.value.youtubeUrl) ?? ''
})

const contentUrl = computed(() => {
    if (!file.value || isFederated.value) return ''
    return knowledgeBase.fileContentUrl(file.value.id)
})

const {loading, error, reload: loadData} = useAsyncLoader(async () => {
    if (props.stationUid) {
        await loadFederatedFile(props.stationUid)
        return
    }

    const fileRes = await knowledgeBase.getFile(props.fileId)
    file.value = fileRes.file
    lastEditedByName.value = fileRes.lastEditedByName
    accessLevel.value = fileRes.accessLevel
    accessLevelSource.value = fileRes.accessLevelSource ?? null

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
        contentMode.value = file.value.contentMode ?? ContentMode.SIMPLE
        blockRows.value = contentMode.value === ContentMode.RICH
            ? toEditRows((await knowledgeBase.getKbBlocks(file.value.id)).rows)
            : []
    } else if (file.value.fileType === KbFileType.TEXT) {
        textContent.value = await knowledgeBase.getTextContent(file.value.id)
        editContent.value = textContent.value
    }
}, {autoLoad: false})

/**
 * Loads a file served by a federation partner. Partners publish the file record and, for textual
 * files, the raw body; tags, related files and the rendered markdown stay on the owning station,
 * so the markdown is rendered here instead.
 */
async function loadFederatedFile(stationUid: string) {
    file.value = await knowledgeBase.getFederatedFile(stationUid, props.fileId)
    lastEditedByName.value = null
    fileTags.value = []
    allStationTags.value = []
    relatedFiles.value = []
    markdownData.value = null
    textContent.value = ''
    accessLevel.value = undefined
    accessLevelSource.value = null

    if (!isTextual.value) return

    const content = await knowledgeBase.getFederatedFileContent(stationUid, props.fileId)
    if (file.value.fileType === KbFileType.MARKDOWN) {
        markdownData.value = {html: renderMarkdown(content), markdown: content}
    } else {
        textContent.value = content
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

/**
 * The saved shape of a block tree turned into the shape the editor works on.
 */
function toEditRows(saved: PageRow[]): RowEditData[] {
    return [...saved]
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map(r => ({
            id: r.id,
            sortOrder: r.sortOrder,
            cells: [...r.cells]
                .sort((a, b) => a.sortOrder - b.sortOrder)
                .map(c => ({
                    id: c.id,
                    sortOrder: c.sortOrder,
                    widthPercent: c.widthPercent,
                    contentType: c.contentType,
                    content: c.content,
                    config: c.config as Record<string, unknown>,
                })),
        }))
}

async function enableBlocks() {
    if (!file.value) return
    const blocks = await knowledgeBase.enableKbBlocks(file.value.id)
    contentMode.value = blocks.contentMode
    blockRows.value = toEditRows(blocks.rows)
    hasUnsavedChanges.value = false
}

async function saveContent() {
    if (!file.value) return
    try {
        if (contentMode.value === ContentMode.RICH) {
            const rows: SaveRowRequest[] = blockRows.value.map((r, ri) => ({
                sortOrder: ri,
                cells: r.cells.map((c, ci): SaveCellRequest => ({
                    sortOrder: ci,
                    widthPercent: c.widthPercent,
                    contentType: c.contentType,
                    content: c.content,
                    config: c.config,
                })),
            }))
            const blocks = await knowledgeBase.saveKbBlocks(file.value.id, rows)
            blockRows.value = toEditRows(blocks.rows)
            markdownData.value = await knowledgeBase.getMarkdownHtml(file.value.id)
            hasUnsavedChanges.value = false
            editing.value = false
            return
        }
        await knowledgeBase.updateMarkdownContent(file.value.id, editContent.value)
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
function goBack() {
    if (file.value?.folderId) {
        router.push({name: routes.value.browse, query: {folderId: file.value.folderId}})
    } else {
        router.push({name: routes.value.browse})
    }
}
const {message: shareCopiedMessage, flash: flashShareCopied} = useFlashMessage(2000)
const shareCopied = computed(() => shareCopiedMessage.value !== '')
function copyShareLink() {
    if (!file.value) return
    const stationUid = getItem('station_id') ?? ''
    const url = `${window.location.origin}/public/station/${stationUid}/knowledge/file/${file.value.id}`
    navigator.clipboard.writeText(url).then(() => flashShareCopied(url))
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

watch(() => [props.fileId, props.stationUid], () => {
    if (loaded.value) loadData()
})
</script>

<template>
    <ViewContent
        :title="isFederated ? t('pages.federated-kb-file.title') : t('pages.kb-file.title')"
        :subtitle="isFederated ? t('pages.federated-kb-file.subtitle') : t('pages.kb-file.subtitle')"
    >
        <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>
        <Spinner v-if="loading"/>

        <template v-else-if="file">
            <KbFileHeaderBar
                :file="file"
                :is-federated="isFederated"
                :is-kb-public="isKbPublic()"
                :share-copied="shareCopied"
                :can-edit="mayEdit"
                :editing="editing"
                @back="goBack"
                @copy-share-link="copyShareLink"
                @copy-to-station="copyToStation"
                @open-edit-metadata="showEditMetadataModal = true"
                @open-share="showShareModal = true"
                @toggle-edit="toggleEdit"
                @open-versions="router.push({name: routes.versions, params: {id: file.id}})"
                @open-presentation="showPresentation = true"
                @download-original="downloadOriginal"
                @download-pdf="downloadPdf"
            />

            <!-- Description -->
            <div v-if="editingDescription" class="flex items-center gap-2 mb-4">
                <TextAreaInput v-model="editDescriptionValue" class="flex-1 !text-sm" :placeholder="t('kb.description')"/>
                <PrimaryButton :aria-label="t('common.save')" :title="t('common.save')" @click="saveDescription">
                    <font-awesome-icon :icon="['fas', 'check']"/>
                </PrimaryButton>
                <SecondaryButton
                    :aria-label="t('common.cancel')"
                    :title="t('common.cancel')"
                    @click="editingDescription = false"
                >
                    <font-awesome-icon :icon="['fas', 'xmark']"/>
                </SecondaryButton>
            </div>
            <MutedText tag="p" size="sm" v-else-if="file.description || canEditDescription" class="group/desc">
                {{ file.description || t('kb.description') }}
                <IconButton
                    v-if="canEditDescription"
                    :icon="['fas', 'pen']"
                    :label="t('kb.edit')"
                    class="opacity-0 group-hover/desc:opacity-100 ml-1 text-[var(--primary)] !p-0 text-xs"
                    @click="startEditDescription"
                />
            </MutedText>

            <p v-if="readOnlyReason" class="text-xs text-[var(--text-muted)] mb-3 flex items-center gap-1">
                <font-awesome-icon :icon="['fas', 'lock']" class="h-3 w-3"/>
                {{ readOnlyReason }}
            </p>

            <!-- Last edit info -->
            <p v-if="file.updatedAt" class="text-xs text-[var(--text-muted)] mb-3">
                {{ t('kb.lastEditedAt') }}: {{ formatDateTime(file.updatedAt) }}
                <span v-if="lastEditedByName">, {{ lastEditedByName }}</span>
            </p>

            <!-- Tags (hide for federated files) -->
            <KbTagsSection
                v-if="!isFederated"
                :tags="fileTags"
                :all-tags="allStationTags"
                :can-manage="mayEdit"
                @add-tag="addTag"
                @remove-tag="removeTag"
            />

            <!-- Related files (hide for federated files) -->
            <KbRelatedFilesSection
                v-if="!isFederated"
                :related-files="relatedFiles"
                :file-id="file.id"
                :can-manage="mayEdit"
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

            <Alert v-if="federatedContentUnavailable" variant="info">
                {{ t('kb.federatedContentUnavailable') }}
            </Alert>
            <KbFileContent
                v-else
                :file="file"
                :editing="editing"
                :content-url="contentUrl"
                :text-content="textContent"
                :rendered-html="renderedHtml"
                :youtube-embed-url="youtubeEmbedUrl"
                :can-edit="mayEdit"
                :content-mode="contentMode"
                :station-uid="blockStationUid"
                v-model:edit-content="editContent"
                v-model:block-rows="blockRows"
                @content-input="onContentInput"
                @enable-blocks="enableBlocks"
                @reupload="handleReuploadFile"
            />

            <!-- Comments -->
            <KbCommentSection
                :file-id="file.id"
                :station-uid="stationUid"
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
        <KbShareModal
            v-model:show="showShareModal"
            :entry="file"
            kind="files"
            @saved="loadData()"
        />

        <KbEditFileModal
            :show="showEditMetadataModal"
            :file="file"
            @update:show="showEditMetadataModal = $event"
            @saved="loadData()"
        />
    </ViewContent>
</template>
