/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import {knowledgeBase} from '@/api'
import SubHeader from '@/components/typography/SubHeader.vue'
import FileInput from '@/components/input/FileInput.vue'

const {t} = useI18n()
const router = useRouter()

const props = defineProps<{
    currentFolderId: number | null
}>()

const emit = defineEmits<{
    created: []
    error: [message: string]
}>()

// Create folder
const showCreateFolderModal = ref(false)
const newFolderName = ref('')
const newFolderDescription = ref('')

// Create markdown file
const showCreateFileModal = ref(false)
const newFileName = ref('')
const newFileDescription = ref('')

// Upload file
const showUploadModal = ref(false)
const uploadFileName = ref('')
const uploadFileDescription = ref('')
const uploadFileRef = ref<File | null>(null)

// YouTube
const showYoutubeModal = ref(false)
const youtubeName = ref('')
const youtubeDescription = ref('')
const youtubeUrl = ref('')

// Link
const showLinkModal = ref(false)
const linkUrl = ref('')
const linkName = ref('')
const linkDescription = ref('')

// Import document
const showImportModal = ref(false)
const importFileName = ref('')
const importFileDescription = ref('')
const importFileRef = ref<File | null>(null)

function openCreateFolder() {
    newFolderName.value = ''
    newFolderDescription.value = ''
    showCreateFolderModal.value = true
}

function openCreateFile() {
    newFileName.value = ''
    newFileDescription.value = ''
    showCreateFileModal.value = true
}

function openUpload() {
    uploadFileName.value = ''
    uploadFileDescription.value = ''
    uploadFileRef.value = null
    showUploadModal.value = true
}

function openYoutube() {
    youtubeName.value = ''
    youtubeDescription.value = ''
    youtubeUrl.value = ''
    showYoutubeModal.value = true
}

function openLink() {
    linkUrl.value = ''
    linkName.value = ''
    linkDescription.value = ''
    showLinkModal.value = true
}

function openImportDocument() {
    importFileName.value = ''
    importFileDescription.value = ''
    importFileRef.value = null
    showImportModal.value = true
}

async function handleCreateFolder() {
    if (!newFolderName.value.trim()) return
    try {
        await knowledgeBase.createFolder({
            parentId: props.currentFolderId,
            name: newFolderName.value.trim(),
            description: newFolderDescription.value,
        })
        showCreateFolderModal.value = false
        emit('created')
    } catch {
        emit('error', t('common.error'))
    }
}

async function handleCreateFile() {
    if (!newFileName.value.trim()) return
    try {
        const file = await knowledgeBase.createMarkdownFile({
            folderId: props.currentFolderId,
            name: newFileName.value.trim(),
            description: newFileDescription.value,
        })
        showCreateFileModal.value = false
        router.push({name: 'kb-file', params: {id: file.id}})
    } catch {
        emit('error', t('common.error'))
    }
}

function onFileSelect(file: File) {
    uploadFileRef.value = file
    if (!uploadFileName.value) {
        uploadFileName.value = file.name
    }
}

async function handleUploadFile() {
    if (!uploadFileRef.value) return
    try {
        await knowledgeBase.uploadFile({
            folderId: props.currentFolderId,
            name: uploadFileName.value || undefined,
            description: uploadFileDescription.value || undefined,
            file: uploadFileRef.value,
        })
        showUploadModal.value = false
        emit('created')
    } catch {
        emit('error', t('common.error'))
    }
}

function onImportFileSelect(file: File) {
    importFileRef.value = file
    if (!importFileName.value) {
        let name = file.name
        const dot = name.lastIndexOf('.')
        if (dot > 0) name = name.substring(0, dot)
        importFileName.value = name
    }
}

async function handleImportDocument() {
    if (!importFileRef.value) return
    try {
        const created = await knowledgeBase.importDocument({
            folderId: props.currentFolderId,
            name: importFileName.value || undefined,
            description: importFileDescription.value || undefined,
            file: importFileRef.value,
        })
        showImportModal.value = false
        router.push({name: 'kb-file', params: {id: created.id}})
    } catch {
        emit('error', t('common.error'))
    }
}

async function handleCreateYoutube() {
    if (!youtubeName.value.trim() || !youtubeUrl.value.trim()) return
    try {
        await knowledgeBase.createYoutubeFile({
            folderId: props.currentFolderId,
            name: youtubeName.value.trim(),
            description: youtubeDescription.value,
            youtubeUrl: youtubeUrl.value.trim(),
        })
        showYoutubeModal.value = false
        emit('created')
    } catch {
        emit('error', t('common.error'))
    }
}

async function handleCreateLink() {
    if (!linkUrl.value.trim()) return
    try {
        await knowledgeBase.createLinkFile({
            folderId: props.currentFolderId,
            name: linkName.value.trim() || undefined,
            description: linkDescription.value.trim() || undefined,
            linkUrl: linkUrl.value.trim(),
        })
        showLinkModal.value = false
        emit('created')
    } catch {
        emit('error', t('common.error'))
    }
}

defineExpose({
    openCreateFolder,
    openCreateFile,
    openUpload,
    openYoutube,
    openLink,
    openImportDocument,
})
</script>

<template>
    <!-- Create Folder Modal -->
    <Modal v-model="showCreateFolderModal">
        <SubHeader class="mb-3">{{ t('kb.newFolder') }}</SubHeader>
        <form @submit.prevent="handleCreateFolder" class="flex flex-col gap-3">
            <TextInput v-model="newFolderName" :placeholder="t('kb.folderName')" required/>
            <TextAreaInput v-model="newFolderDescription" :placeholder="t('kb.description')"/>
            <PrimaryButton type="submit">{{ t('kb.newFolder') }}</PrimaryButton>
        </form>
    </Modal>

    <!-- Create File Modal -->
    <Modal v-model="showCreateFileModal">
        <SubHeader class="mb-3">{{ t('kb.newFile') }}</SubHeader>
        <form @submit.prevent="handleCreateFile" class="flex flex-col gap-3">
            <TextInput v-model="newFileName" :placeholder="t('kb.fileName')" required/>
            <TextAreaInput v-model="newFileDescription" :placeholder="t('kb.description')"/>
            <PrimaryButton type="submit">{{ t('kb.newFile') }}</PrimaryButton>
        </form>
    </Modal>

    <!-- Upload File Modal -->
    <Modal v-model="showUploadModal">
        <SubHeader class="mb-3">{{ t('kb.uploadFile') }}</SubHeader>
        <form @submit.prevent="handleUploadFile" class="flex flex-col gap-3">
            <FileInput @select="onFileSelect"/>
            <TextInput v-model="uploadFileName" :placeholder="t('kb.fileName')"/>
            <TextAreaInput v-model="uploadFileDescription" :placeholder="t('kb.description')"/>
            <PrimaryButton type="submit" :disabled="!uploadFileRef">{{ t('kb.uploadFile') }}</PrimaryButton>
        </form>
    </Modal>

    <!-- Import Document Modal -->
    <Modal v-model="showImportModal">
        <SubHeader class="mb-3">{{ t('kb.importDocument') }}</SubHeader>
        <p class="text-sm text-[var(--text-muted)] mb-3">{{ t('kb.importDocumentHint') }}</p>
        <form @submit.prevent="handleImportDocument" class="flex flex-col gap-3">
            <FileInput accept=".docx,.odt,.rtf,.html,.htm,.epub,.tex" @select="onImportFileSelect"/>
            <TextInput v-model="importFileName" :placeholder="t('kb.fileName')" />
            <TextAreaInput v-model="importFileDescription" :placeholder="t('kb.description')" />
            <div class="flex gap-2 justify-end">
                <SecondaryButton type="button" @click="showImportModal = false">{{ t('common.cancel') }}</SecondaryButton>
                <PrimaryButton type="submit" :disabled="!importFileRef">{{ t('kb.importBtn') }}</PrimaryButton>
            </div>
        </form>
    </Modal>

    <!-- YouTube Modal -->
    <Modal v-model="showYoutubeModal">
        <SubHeader class="mb-3">{{ t('kb.addYoutube') }}</SubHeader>
        <form @submit.prevent="handleCreateYoutube" class="flex flex-col gap-3">
            <TextInput v-model="youtubeName" :placeholder="t('kb.fileName')" required/>
            <TextInput v-model="youtubeUrl" :placeholder="t('kb.youtubeUrl')" required/>
            <TextAreaInput v-model="youtubeDescription" :placeholder="t('kb.description')"/>
            <PrimaryButton type="submit">{{ t('kb.addYoutube') }}</PrimaryButton>
        </form>
    </Modal>

    <!-- Link Modal -->
    <Modal v-model="showLinkModal">
        <SubHeader class="mb-3">{{ t('kb.addLink') }}</SubHeader>
        <form @submit.prevent="handleCreateLink" class="flex flex-col gap-3">
            <TextInput v-model="linkUrl" :placeholder="t('kb.linkUrl')" required/>
            <p class="text-xs text-[var(--text-muted)]">{{ t('kb.linkAutoFetch') }}</p>
            <TextInput v-model="linkName" :placeholder="t('kb.fileName')"/>
            <TextAreaInput v-model="linkDescription" :placeholder="t('kb.description')"/>
            <PrimaryButton type="submit">{{ t('kb.addLink') }}</PrimaryButton>
        </form>
    </Modal>
</template>
