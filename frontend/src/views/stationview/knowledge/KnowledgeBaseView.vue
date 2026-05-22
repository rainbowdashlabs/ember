/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, onMounted, watch, computed, onBeforeUnmount} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import {useSession} from '@/composables/useSession'
import {knowledgeBase, stationMembers, memberGroups, userTags} from '@/api'
import type {KbFolder, KbFile} from '@/api/knowledgeBase'
import {KbFileType} from '@/api/knowledgeBase'
import type {Role, MemberGroup, UserTag} from '@/api/types'

const {t} = useI18n()
const router = useRouter()
const route = useRoute()
const {canManageKnowledge, loaded} = useSession()

// View mode
const viewMode = ref<'grid' | 'list'>('grid')

const loading = ref(true)
const error = ref('')
const currentFolder = ref<KbFolder | null>(null)
const folders = ref<KbFolder[]>([])
const files = ref<KbFile[]>([])

// Breadcrumb
const breadcrumbs = ref<KbFolder[]>([])

// Search
const searchQuery = ref('')
const searchResults = ref<import('@/api/knowledgeBase').SearchResult[]>([])
const searching = ref(false)
const isSearching = computed(() => searchQuery.value.trim().length > 0)

// Create dropdown
const showCreateDropdown = ref(false)

function closeDropdown() {
    showCreateDropdown.value = false
}

function onClickOutside(event: MouseEvent) {
    const target = event.target as HTMLElement
    if (!target.closest('.create-dropdown-container')) {
        closeDropdown()
    }
}

onMounted(() => {
    document.addEventListener('click', onClickOutside)
})

onBeforeUnmount(() => {
    document.removeEventListener('click', onClickOutside)
})

// Create folder modal
const showCreateFolderModal = ref(false)
const newFolderName = ref('')
const newFolderDescription = ref('')

// Create markdown file modal
const showCreateFileModal = ref(false)
const newFileName = ref('')
const newFileDescription = ref('')

// Upload file modal
const showUploadModal = ref(false)
const uploadFileName = ref('')
const uploadFileDescription = ref('')
const uploadFileRef = ref<File | null>(null)

// YouTube modal
const showYoutubeModal = ref(false)
const youtubeName = ref('')
const youtubeDescription = ref('')
const youtubeUrl = ref('')

// Link modal
const showLinkModal = ref(false)
const linkUrl = ref('')
const linkName = ref('')
const linkDescription = ref('')

// Import document modal
const showImportModal = ref(false)
const importFileName = ref('')
const importFileDescription = ref('')
const importFileRef = ref<File | null>(null)

// Edit folder modal
const showEditFolderModal = ref(false)
const editFolderData = ref<KbFolder | null>(null)
const editFolderName = ref('')
const editFolderDescription = ref('')

// Edit file modal
const showEditFileModal = ref(false)
const editFileData = ref<KbFile | null>(null)
const editFileName = ref('')
const editFileDescription = ref('')

// Delete modals
const showDeleteFolderModal = ref(false)
const folderToDelete = ref<KbFolder | null>(null)
const showDeleteFileModal = ref(false)
const fileToDelete = ref<KbFile | null>(null)

// Restrictions (for edit modals)
const allRoles = ref<Role[]>([])
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const editFolderRoleIds = ref<Set<number>>(new Set())
const editFolderGroupIds = ref<Set<number>>(new Set())
const editFolderTagIds = ref<Set<number>>(new Set())
const editFileRoleIds = ref<Set<number>>(new Set())
const editFileGroupIds = ref<Set<number>>(new Set())
const editFileTagIds = ref<Set<number>>(new Set())
const restrictionsDirty = ref(false)
const editFolderTags = ref<string[]>([])
const editFileTags = ref<string[]>([])
const newEditFolderTag = ref('')
const newEditFileTag = ref('')
const folderIconFile = ref<File | null>(null)

function onFolderIconSelect(event: Event) {
    const input = event.target as HTMLInputElement
    folderIconFile.value = input.files?.[0] ?? null
}

const currentFolderId = computed(() => {
    const param = route.query.folderId
    return param ? Number(param) : null
})

async function loadRestrictionOptions() {
    try {
        const [roleList, groupList, tagList] = await Promise.all([
            stationMembers.listAllRoles(),
            memberGroups.listGroups(),
            userTags.listTags(),
        ])
        allRoles.value = roleList
        allGroups.value = groupList
        allTags.value = tagList
    } catch {
        // ignore
    }
}

async function loadData() {
    loading.value = true
    error.value = ''
    try {
        const result = await knowledgeBase.browse(currentFolderId.value)
        currentFolder.value = result.currentFolder
        folders.value = result.folders
        files.value = result.files
        await buildBreadcrumbs()
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
}

async function buildBreadcrumbs() {
    const crumbs: KbFolder[] = []
    let folder = currentFolder.value
    while (folder) {
        crumbs.unshift(folder)
        if (folder.parentId) {
            try {
                folder = await knowledgeBase.getFolder(folder.parentId)
            } catch {
                break
            }
        } else {
            break
        }
    }
    breadcrumbs.value = crumbs
}

function navigateToFolder(folderId: number | null) {
    if (folderId === null) {
        router.push({name: 'kb-browse'})
    } else {
        router.push({name: 'kb-browse', query: {folderId}})
    }
}

function navigateToFile(file: KbFile) {
    router.push({name: 'kb-file', params: {id: file.id}})
}

function fileIcon(file: KbFile): string[] {
    switch (file.fileType) {
        case KbFileType.MARKDOWN:
        case KbFileType.TEXT:
            return ['fas', 'file-lines']
        case KbFileType.PDF:
            return ['fas', 'file-pdf']
        case KbFileType.IMAGE:
            return ['fas', 'image']
        case KbFileType.YOUTUBE:
            return ['fab', 'youtube']
        case KbFileType.LINK:
            return ['fas', 'link']
        default:
            return ['fas', 'file']
    }
}

function fileTypeLabel(file: KbFile): string {
    switch (file.fileType) {
        case KbFileType.MARKDOWN:
            return 'Markdown'
        case KbFileType.PDF:
            return 'PDF'
        case KbFileType.TEXT:
            return 'Text'
        case KbFileType.IMAGE:
            return 'Bild'
        case KbFileType.YOUTUBE:
            return 'YouTube'
        case KbFileType.LINK:
            return 'Link'
        default:
            return 'Datei'
    }
}

function formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('de-DE', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
    })
}

let searchTimeout: ReturnType<typeof setTimeout> | null = null

function onSearchInput() {
    if (searchTimeout) clearTimeout(searchTimeout)
    if (!searchQuery.value.trim()) {
        searchResults.value = []
        return
    }
    searchTimeout = setTimeout(async () => {
        searching.value = true
        try {
            searchResults.value = await knowledgeBase.search(searchQuery.value.trim())
        } catch {
            searchResults.value = []
        } finally {
            searching.value = false
        }
    }, 300)
}

// -- Dropdown actions --

function openCreateFolder() {
    closeDropdown()
    showCreateFolderModal.value = true
}

function openCreateFile() {
    closeDropdown()
    showCreateFileModal.value = true
}

function openUpload() {
    closeDropdown()
    showUploadModal.value = true
}

function openYoutube() {
    closeDropdown()
    showYoutubeModal.value = true
}

function openLink() {
    closeDropdown()
    showLinkModal.value = true
}

function openImportDocument() {
    closeDropdown()
    importFileName.value = ''
    importFileDescription.value = ''
    importFileRef.value = null
    showImportModal.value = true
}

// -- Folder CRUD --

async function handleCreateFolder() {
    if (!newFolderName.value.trim()) return
    try {
        await knowledgeBase.createFolder({
            parentId: currentFolderId.value,
            name: newFolderName.value.trim(),
            description: newFolderDescription.value,
        })
        showCreateFolderModal.value = false
        newFolderName.value = ''
        newFolderDescription.value = ''
        await loadData()
    } catch {
        error.value = t('common.error')
    }
}

async function openEditFolder(folder: KbFolder) {
    editFolderData.value = folder
    editFolderName.value = folder.name
    editFolderDescription.value = folder.description
    editFolderRoleIds.value = new Set()
    editFolderGroupIds.value = new Set()
    editFolderTagIds.value = new Set()
    editFolderTags.value = []
    newEditFolderTag.value = ''
    restrictionsDirty.value = false
    showEditFolderModal.value = true
    await loadRestrictionOptions()
    try {
        const [r, tags] = await Promise.all([
            knowledgeBase.getFolderRestrictions(folder.id),
            knowledgeBase.getFolderTags(folder.id),
        ])
        editFolderRoleIds.value = new Set(r.roleIds)
        editFolderGroupIds.value = new Set(r.groupIds)
        editFolderTagIds.value = new Set(r.tagIds)
        editFolderTags.value = tags.map(t => t.name)
    } catch {
        // ignore
    }
}

async function handleEditFolder() {
    if (!editFolderData.value || !editFolderName.value.trim()) return
    try {
        const promises: Promise<unknown>[] = [
            knowledgeBase.updateFolder(editFolderData.value.id, {
                name: editFolderName.value.trim(),
                description: editFolderDescription.value,
            }),
            knowledgeBase.setFolderRestrictions(editFolderData.value.id, {
                roleIds: [...editFolderRoleIds.value],
                groupIds: [...editFolderGroupIds.value],
                tagIds: [...editFolderTagIds.value],
                memberIds: [],
            }),
            knowledgeBase.setFolderTags(editFolderData.value.id, editFolderTags.value),
        ]
        if (folderIconFile.value) {
            promises.push(knowledgeBase.uploadFolderIcon(editFolderData.value.id, folderIconFile.value))
        }
        await Promise.all(promises)
        showEditFolderModal.value = false
        folderIconFile.value = null
        await loadData()
    } catch {
        error.value = t('common.error')
    }
}

function confirmDeleteFolder(folder: KbFolder) {
    folderToDelete.value = folder
    showDeleteFolderModal.value = true
}

async function handleDeleteFolder() {
    if (!folderToDelete.value) return
    try {
        await knowledgeBase.deleteFolder(folderToDelete.value.id)
        showDeleteFolderModal.value = false
        folderToDelete.value = null
        await loadData()
    } catch {
        error.value = t('common.error')
    }
}

// -- File CRUD --

async function handleCreateFile() {
    if (!newFileName.value.trim()) return
    try {
        const file = await knowledgeBase.createMarkdownFile({
            folderId: currentFolderId.value,
            name: newFileName.value.trim(),
            description: newFileDescription.value,
        })
        showCreateFileModal.value = false
        newFileName.value = ''
        newFileDescription.value = ''
        router.push({name: 'kb-file', params: {id: file.id}})
    } catch {
        error.value = t('common.error')
    }
}

function onFileSelect(event: Event) {
    const input = event.target as HTMLInputElement
    if (input.files && input.files.length > 0) {
        uploadFileRef.value = input.files[0]
        if (!uploadFileName.value) {
            uploadFileName.value = input.files[0].name
        }
    }
}

async function handleUploadFile() {
    if (!uploadFileRef.value) return
    try {
        await knowledgeBase.uploadFile({
            folderId: currentFolderId.value,
            name: uploadFileName.value || undefined,
            description: uploadFileDescription.value || undefined,
            file: uploadFileRef.value,
        })
        showUploadModal.value = false
        uploadFileName.value = ''
        uploadFileDescription.value = ''
        uploadFileRef.value = null
        await loadData()
    } catch {
        error.value = t('common.error')
    }
}

function onImportFileSelect(event: Event) {
    const input = event.target as HTMLInputElement
    if (input.files && input.files.length > 0) {
        importFileRef.value = input.files[0]
        if (!importFileName.value) {
            let name = input.files[0].name
            const dot = name.lastIndexOf('.')
            if (dot > 0) name = name.substring(0, dot)
            importFileName.value = name
        }
    }
}

async function handleImportDocument() {
    if (!importFileRef.value) return
    try {
        const created = await knowledgeBase.importDocument({
            folderId: currentFolderId.value,
            name: importFileName.value || undefined,
            description: importFileDescription.value || undefined,
            file: importFileRef.value,
        })
        showImportModal.value = false
        importFileName.value = ''
        importFileDescription.value = ''
        importFileRef.value = null
        router.push({name: 'kb-file', params: {id: created.id}})
    } catch {
        error.value = t('common.error')
    }
}

async function handleCreateYoutube() {
    if (!youtubeName.value.trim() || !youtubeUrl.value.trim()) return
    try {
        await knowledgeBase.createYoutubeFile({
            folderId: currentFolderId.value,
            name: youtubeName.value.trim(),
            description: youtubeDescription.value,
            youtubeUrl: youtubeUrl.value.trim(),
        })
        showYoutubeModal.value = false
        youtubeName.value = ''
        youtubeDescription.value = ''
        youtubeUrl.value = ''
        await loadData()
    } catch {
        error.value = t('common.error')
    }
}

async function handleCreateLink() {
    if (!linkUrl.value.trim()) return
    try {
        await knowledgeBase.createLinkFile({
            folderId: currentFolderId.value,
            name: linkName.value.trim() || undefined,
            description: linkDescription.value.trim() || undefined,
            linkUrl: linkUrl.value.trim(),
        })
        showLinkModal.value = false
        linkUrl.value = ''
        linkName.value = ''
        linkDescription.value = ''
        await loadData()
    } catch {
        error.value = t('common.error')
    }
}

async function openEditFile(file: KbFile) {
    editFileData.value = file
    editFileName.value = file.name
    editFileDescription.value = file.description
    editFileRoleIds.value = new Set()
    editFileGroupIds.value = new Set()
    editFileTagIds.value = new Set()
    editFileTags.value = []
    newEditFileTag.value = ''
    restrictionsDirty.value = false
    showEditFileModal.value = true
    await loadRestrictionOptions()
    try {
        const [r, tags] = await Promise.all([
            knowledgeBase.getFileRestrictions(file.id),
            knowledgeBase.getFileTags(file.id),
        ])
        editFileRoleIds.value = new Set(r.roleIds)
        editFileGroupIds.value = new Set(r.groupIds)
        editFileTagIds.value = new Set(r.tagIds)
        editFileTags.value = tags.map(t => t.name)
    } catch {
        // ignore
    }
}

async function handleEditFile() {
    if (!editFileData.value || !editFileName.value.trim()) return
    try {
        await Promise.all([
            knowledgeBase.updateFile(editFileData.value.id, {
                name: editFileName.value.trim(),
                description: editFileDescription.value,
            }),
            knowledgeBase.setFileRestrictions(editFileData.value.id, {
                roleIds: [...editFileRoleIds.value],
                groupIds: [...editFileGroupIds.value],
                tagIds: [...editFileTagIds.value],
                memberIds: [],
            }),
            knowledgeBase.setFileTags(editFileData.value.id, editFileTags.value),
        ])
        showEditFileModal.value = false
        await loadData()
    } catch {
        error.value = t('common.error')
    }
}

function confirmDeleteFile(file: KbFile) {
    fileToDelete.value = file
    showDeleteFileModal.value = true
}

async function handleDeleteFile() {
    if (!fileToDelete.value) return
    try {
        await knowledgeBase.deleteFile(fileToDelete.value.id)
        showDeleteFileModal.value = false
        fileToDelete.value = null
        await loadData()
    } catch {
        error.value = t('common.error')
    }
}

function toggleFolderRole(roleId: number) {
    const next = new Set(editFolderRoleIds.value)
    if (next.has(roleId)) next.delete(roleId)
    else next.add(roleId)
    editFolderRoleIds.value = next
}

function toggleFolderGroup(groupId: number) {
    const next = new Set(editFolderGroupIds.value)
    if (next.has(groupId)) next.delete(groupId)
    else next.add(groupId)
    editFolderGroupIds.value = next
}

function toggleFolderTag(tagId: number) {
    const next = new Set(editFolderTagIds.value)
    if (next.has(tagId)) next.delete(tagId)
    else next.add(tagId)
    editFolderTagIds.value = next
}

function toggleFileRole(roleId: number) {
    const next = new Set(editFileRoleIds.value)
    if (next.has(roleId)) next.delete(roleId)
    else next.add(roleId)
    editFileRoleIds.value = next
}

function toggleFileGroup(groupId: number) {
    const next = new Set(editFileGroupIds.value)
    if (next.has(groupId)) next.delete(groupId)
    else next.add(groupId)
    editFileGroupIds.value = next
}

function toggleFileTag(tagId: number) {
    const next = new Set(editFileTagIds.value)
    if (next.has(tagId)) next.delete(tagId)
    else next.add(tagId)
    editFileTagIds.value = next
}

watch(() => route.query.folderId, () => {
    loadData()
})

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

        <!-- Search -->
        <div class="mb-4">
            <TextInput
                v-model="searchQuery"
                :placeholder="t('kb.search')"
                @input="onSearchInput"
            />
        </div>

        <!-- Breadcrumbs + View Toggle -->
        <div v-if="!isSearching" class="flex items-center justify-between mb-4">
            <nav class="flex items-center gap-1 text-sm flex-wrap">
            <button
                v-if="currentFolder"
                class="p-1 rounded hover:bg-[var(--bg-accent)] transition-colors cursor-pointer mr-1"
                :title="t('kb.goUp')"
                @click="navigateToFolder(currentFolder.parentId)"
            >
                <font-awesome-icon :icon="['fas', 'chevron-up']" class="text-xs"/>
            </button>
            <button
                class="hover:text-[var(--primary)] transition-colors cursor-pointer"
                :class="{'font-semibold text-[var(--primary)]': !currentFolder}"
                @click="navigateToFolder(null)"
            >
                {{ t('kb.root') }}
            </button>
            <template v-for="crumb in breadcrumbs" :key="crumb.id">
                <font-awesome-icon :icon="['fas', 'chevron-right']" class="text-xs text-[var(--text-muted)]"/>
                <button
                    class="hover:text-[var(--primary)] transition-colors cursor-pointer"
                    :class="{'font-semibold text-[var(--primary)]': crumb.id === currentFolder?.id}"
                    @click="navigateToFolder(crumb.id)"
                >
                    {{ crumb.name }}
                </button>
            </template>
            </nav>
            <button
                class="p-2 rounded hover:bg-[var(--bg-accent)] transition-colors cursor-pointer"
                :title="viewMode === 'grid' ? t('kb.listView') : t('kb.gridView')"
                @click="viewMode = viewMode === 'grid' ? 'list' : 'grid'"
            >
                <font-awesome-icon
                    :icon="['fas', viewMode === 'grid' ? 'table-columns' : 'grip-vertical']"
                    class="text-sm"
                />
            </button>
        </div>

        <!-- Search Results -->
        <div v-if="isSearching">
            <h2 class="text-lg font-semibold mb-3">{{ t('kb.searchResults') }}</h2>
            <Spinner v-if="searching"/>
            <p v-else-if="searchResults.length === 0" class="text-[var(--text-muted)]">
                {{ t('kb.noResults') }}
            </p>
            <div v-else class="flex flex-col gap-2">
                <NeutralContainer
                    v-for="result in searchResults"
                    :key="result.file.id"
                    class="cursor-pointer hover:border-[var(--primary)] transition-colors"
                    @click="navigateToFile(result.file)"
                >
                    <div class="flex items-start gap-3 p-2">
                        <font-awesome-icon :icon="fileIcon(result.file)" class="text-xl text-[var(--primary)] mt-0.5"/>
                        <div class="flex-1 min-w-0">
                            <span class="text-sm font-medium">{{ result.file.name }}</span>
                            <p v-if="result.snippet" class="search-snippet text-xs text-[var(--text-muted)] mt-1 line-clamp-2" v-html="result.snippet"/>
                            <p v-else-if="result.file.description" class="text-xs text-[var(--text-muted)] mt-1 truncate">
                                {{ result.file.description }}
                            </p>
                        </div>
                    </div>
                </NeutralContainer>
            </div>
        </div>

        <!-- Browse -->
        <div v-else>
            <Spinner v-if="loading"/>
            <template v-else>
                <!-- Folder description -->
                <p v-if="currentFolder?.description" class="text-sm text-[var(--text-muted)] mb-4">
                    {{ currentFolder.description }}
                </p>

                <!-- Manager actions: single "Neu" dropdown -->
                <div v-if="canManageKnowledge()" class="flex flex-wrap gap-2 mb-4">
                    <div class="relative create-dropdown-container">
                        <PrimaryButton @click="showCreateDropdown = !showCreateDropdown">
                            <font-awesome-icon :icon="['fas', 'plus']"/>
                            {{ t('kb.newEntry') }}
                            <font-awesome-icon :icon="['fas', 'chevron-down']" class="text-xs ml-1"/>
                        </PrimaryButton>
                        <div
                            v-if="showCreateDropdown"
                            class="absolute left-0 top-full mt-1 z-20 min-w-48 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark shadow-lg py-1"
                        >
                            <DropdownMenuItem :icon="['fas', 'folder']" icon-class="text-[var(--accent)]" @click="openCreateFolder">
                                {{ t('kb.newFolder') }}
                            </DropdownMenuItem>
                            <DropdownMenuItem :icon="['fas', 'file-lines']" @click="openCreateFile">
                                {{ t('kb.newMarkdownFile') }}
                            </DropdownMenuItem>
                            <DropdownMenuItem :icon="['fas', 'upload']" @click="openUpload">
                                {{ t('kb.uploadFile') }}
                            </DropdownMenuItem>
                            <DropdownMenuItem :icon="['fab', 'youtube']" icon-class="text-red-600" @click="openYoutube">
                                {{ t('kb.addYoutube') }}
                            </DropdownMenuItem>
                            <DropdownMenuItem :icon="['fas', 'link']" icon-class="text-[var(--secondary)]" @click="openLink">
                                {{ t('kb.addLink') }}
                            </DropdownMenuItem>
                            <DropdownMenuItem :icon="['fas', 'file-import']" @click="openImportDocument">
                                {{ t('kb.importDocument') }}
                            </DropdownMenuItem>
                        </div>
                    </div>
                </div>

                <!-- Content: Grid or List -->
                <template v-if="folders.length > 0 || files.length > 0">
                    <!-- Grid View -->
                    <div v-if="viewMode === 'grid'"
                         class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
                        <!-- Folders -->
                        <NeutralContainer
                            v-for="folder in folders"
                            :key="'folder-' + folder.id"
                            class="cursor-pointer hover:border-[var(--primary)] transition-colors relative group"
                            @click="navigateToFolder(folder.id)"
                        >
                            <div class="flex flex-col items-center gap-2 p-2 text-center">
                                <img
                                    v-if="folder.iconUrl"
                                    :src="knowledgeBase.folderIconUrl(folder.id)"
                                    :alt="folder.name"
                                    class="w-8 h-8 rounded object-cover"
                                    @error="($event.target as HTMLImageElement).style.display = 'none'"
                                />
                                <font-awesome-icon v-else :icon="['fas', 'folder']"
                                                   class="text-2xl text-[var(--accent)]"/>
                                <span class="text-sm font-medium truncate w-full">{{ folder.name }}</span>
                                <span v-if="folder.description"
                                      class="text-xs text-[var(--text-muted)] truncate w-full">
                                    {{ folder.description }}
                                </span>
                            </div>
                            <div v-if="canManageKnowledge()"
                                 class="absolute top-1 right-1 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                <EditButton
                                    :label="t('kb.editFolder')"
                                    @click.stop="openEditFolder(folder)"
                                />
                                <DeleteButton
                                    :label="t('kb.deleteFolder')"
                                    @click.stop="confirmDeleteFolder(folder)"
                                />
                            </div>
                        </NeutralContainer>

                        <!-- Files -->
                        <NeutralContainer
                            v-for="file in files"
                            :key="'file-' + file.id"
                            class="cursor-pointer hover:border-[var(--primary)] transition-colors relative group"
                            @click="navigateToFile(file)"
                        >
                            <div class="flex flex-col items-center gap-2 p-2 text-center">
                                <font-awesome-icon :icon="fileIcon(file)" class="text-2xl text-[var(--primary)]"/>
                                <span class="text-sm font-medium truncate w-full">{{ file.name }}</span>
                                <span v-if="file.description"
                                      class="text-xs text-[var(--text-muted)] truncate w-full">
                                    {{ file.description }}
                                </span>
                            </div>
                            <div v-if="canManageKnowledge()"
                                 class="absolute top-1 right-1 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                <EditButton
                                    :label="t('kb.editFile')"
                                    @click.stop="openEditFile(file)"
                                />
                                <DeleteButton
                                    :label="t('kb.deleteFile')"
                                    @click.stop="confirmDeleteFile(file)"
                                />
                            </div>
                        </NeutralContainer>
                    </div>

                    <!-- List View -->
                    <div v-else class="border border-[var(--border)] rounded-lg overflow-hidden divide-y divide-[var(--border)]">
                        <!-- Folders -->
                        <div
                            v-for="folder in folders"
                            :key="'folder-' + folder.id"
                            class="flex items-center gap-3 px-3 py-1.5 cursor-pointer hover:bg-[var(--bg-accent)] transition-colors group"
                            @click="navigateToFolder(folder.id)"
                        >
                            <div class="w-5 flex-shrink-0 flex justify-center">
                                <img
                                    v-if="folder.iconUrl"
                                    :src="knowledgeBase.folderIconUrl(folder.id)"
                                    :alt="folder.name"
                                    class="w-4 h-4 rounded object-cover"
                                    @error="($event.target as HTMLImageElement).style.display = 'none'"
                                />
                                <font-awesome-icon v-else :icon="['fas', 'folder']"
                                                   class="text-sm text-[var(--accent)]"/>
                            </div>
                            <span class="text-sm font-medium truncate min-w-0 flex-1">{{ folder.name }}</span>
                            <span v-if="folder.description"
                                  class="hidden sm:block text-xs text-[var(--text-muted)] truncate max-w-48">
                                {{ folder.description }}
                            </span>
                            <span class="hidden md:block text-xs text-[var(--text-muted)] w-16 text-right flex-shrink-0">Ordner</span>
                            <span class="hidden md:block text-xs text-[var(--text-muted)] w-24 text-right flex-shrink-0">
                                {{ formatDate(folder.updatedAt) }}
                            </span>
                            <div v-if="canManageKnowledge()"
                                 class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0">
                                <EditButton
                                    :label="t('kb.editFolder')"
                                    @click.stop="openEditFolder(folder)"
                                />
                                <DeleteButton
                                    :label="t('kb.deleteFolder')"
                                    @click.stop="confirmDeleteFolder(folder)"
                                />
                            </div>
                        </div>

                        <!-- Files -->
                        <div
                            v-for="file in files"
                            :key="'file-' + file.id"
                            class="flex items-center gap-3 px-3 py-1.5 cursor-pointer hover:bg-[var(--bg-accent)] transition-colors group"
                            @click="navigateToFile(file)"
                        >
                            <div class="w-5 flex-shrink-0 flex justify-center">
                                <font-awesome-icon :icon="fileIcon(file)" class="text-sm text-[var(--primary)]"/>
                            </div>
                            <span class="text-sm font-medium truncate min-w-0 flex-1">{{ file.name }}</span>
                            <span v-if="file.description"
                                  class="hidden sm:block text-xs text-[var(--text-muted)] truncate max-w-48">
                                {{ file.description }}
                            </span>
                            <span class="hidden md:block text-xs text-[var(--text-muted)] w-16 text-right flex-shrink-0">
                                {{ fileTypeLabel(file) }}
                            </span>
                            <span class="hidden md:block text-xs text-[var(--text-muted)] w-24 text-right flex-shrink-0">
                                {{ formatDate(file.updatedAt) }}
                            </span>
                            <div v-if="canManageKnowledge()"
                                 class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0">
                                <EditButton
                                    :label="t('kb.editFile')"
                                    @click.stop="openEditFile(file)"
                                />
                                <DeleteButton
                                    :label="t('kb.deleteFile')"
                                    @click.stop="confirmDeleteFile(file)"
                                />
                            </div>
                        </div>
                    </div>
                </template>

                <p v-else class="text-[var(--text-muted)] text-center py-8">
                    {{ t('kb.emptyFolder') }}
                </p>
            </template>
        </div>

        <!-- Create Folder Modal -->
        <Modal v-model="showCreateFolderModal">
            <h3 class="text-lg font-semibold mb-3">{{ t('kb.newFolder') }}</h3>
            <form @submit.prevent="handleCreateFolder" class="flex flex-col gap-3">
                <TextInput v-model="newFolderName" :placeholder="t('kb.folderName')" required/>
                <TextAreaInput v-model="newFolderDescription" :placeholder="t('kb.description')"/>
                <PrimaryButton type="submit">{{ t('kb.newFolder') }}</PrimaryButton>
            </form>
        </Modal>

        <!-- Create File Modal -->
        <Modal v-model="showCreateFileModal">
            <h3 class="text-lg font-semibold mb-3">{{ t('kb.newFile') }}</h3>
            <form @submit.prevent="handleCreateFile" class="flex flex-col gap-3">
                <TextInput v-model="newFileName" :placeholder="t('kb.fileName')" required/>
                <TextAreaInput v-model="newFileDescription" :placeholder="t('kb.description')"/>
                <PrimaryButton type="submit">{{ t('kb.newFile') }}</PrimaryButton>
            </form>
        </Modal>

        <!-- Upload File Modal -->
        <Modal v-model="showUploadModal">
            <h3 class="text-lg font-semibold mb-3">{{ t('kb.uploadFile') }}</h3>
            <form @submit.prevent="handleUploadFile" class="flex flex-col gap-3">
                <input
                    ref="fileInputRef"
                    type="file"
                    class="block w-full text-sm text-[var(--text)] file:mr-4 file:py-2 file:px-4 file:rounded file:border-0 file:text-sm file:font-semibold file:bg-[var(--primary)] file:text-white hover:file:bg-[var(--primary-accent)] cursor-pointer"
                    @change="onFileSelect"
                />
                <TextInput v-model="uploadFileName" :placeholder="t('kb.fileName')"/>
                <TextAreaInput v-model="uploadFileDescription" :placeholder="t('kb.description')"/>
                <PrimaryButton type="submit" :disabled="!uploadFileRef">{{ t('kb.uploadFile') }}</PrimaryButton>
            </form>
        </Modal>

        <!-- Import Document Modal -->
        <Modal v-model="showImportModal">
            <h3 class="text-lg font-semibold mb-3">{{ t('kb.importDocument') }}</h3>
            <p class="text-sm text-[var(--text-muted)] mb-3">{{ t('kb.importDocumentHint') }}</p>
            <form @submit.prevent="handleImportDocument" class="flex flex-col gap-3">
                <input type="file" accept=".docx,.odt,.rtf,.html,.htm,.epub,.tex" @change="onImportFileSelect" />
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
            <h3 class="text-lg font-semibold mb-3">{{ t('kb.addYoutube') }}</h3>
            <form @submit.prevent="handleCreateYoutube" class="flex flex-col gap-3">
                <TextInput v-model="youtubeName" :placeholder="t('kb.fileName')" required/>
                <TextInput v-model="youtubeUrl" :placeholder="t('kb.youtubeUrl')" required/>
                <TextAreaInput v-model="youtubeDescription" :placeholder="t('kb.description')"/>
                <PrimaryButton type="submit">{{ t('kb.addYoutube') }}</PrimaryButton>
            </form>
        </Modal>

        <!-- Link Modal -->
        <Modal v-model="showLinkModal">
            <h3 class="text-lg font-semibold mb-3">{{ t('kb.addLink') }}</h3>
            <form @submit.prevent="handleCreateLink" class="flex flex-col gap-3">
                <TextInput v-model="linkUrl" :placeholder="t('kb.linkUrl')" required/>
                <p class="text-xs text-[var(--text-muted)]">{{ t('kb.linkAutoFetch') }}</p>
                <TextInput v-model="linkName" :placeholder="t('kb.fileName')"/>
                <TextAreaInput v-model="linkDescription" :placeholder="t('kb.description')"/>
                <PrimaryButton type="submit">{{ t('kb.addLink') }}</PrimaryButton>
            </form>
        </Modal>

        <!-- Edit Folder Modal -->
        <Modal v-model="showEditFolderModal">
            <h3 class="text-lg font-semibold mb-3">{{ t('kb.editFolder') }}</h3>
            <form @submit.prevent="handleEditFolder" class="flex flex-col gap-3">
                <TextInput v-model="editFolderName" :placeholder="t('kb.folderName')" required/>
                <TextAreaInput v-model="editFolderDescription" :placeholder="t('kb.description')"/>

                <!-- Folder icon -->
                <div class="space-y-2">
                    <label class="text-xs text-[var(--text-muted)]">{{ t('kb.folderIcon') }}</label>
                    <input
                        type="file"
                        accept="image/png,image/jpeg,image/webp"
                        class="block w-full text-sm text-[var(--text)] file:mr-4 file:py-1 file:px-3 file:rounded file:border-0 file:text-xs file:font-semibold file:bg-[var(--primary)] file:text-white cursor-pointer"
                        @change="onFolderIconSelect"
                    />
                </div>

                <!-- Restrictions -->
                <div class="space-y-3 border-t border-bg-light-accent dark:border-bg-dark-accent pt-3">
                    <h3 class="text-sm font-semibold">{{ t('kb.restrictions') }}</h3>
                    <div class="space-y-2">
                        <label class="text-xs text-[var(--text-muted)]">{{ t('kb.restrictionRoles') }}</label>
                        <div class="flex flex-wrap gap-2">
                            <button v-for="role in allRoles" :key="role.id" type="button"
                                    :class="editFolderRoleIds.has(role.id) ? 'border-primary bg-primary/15 text-primary' : 'border-bg-light-accent dark:border-bg-dark-accent text-[var(--text-muted)] hover:border-primary'"
                                    class="px-2 py-1 text-xs rounded border transition-colors"
                                    @click="toggleFolderRole(role.id)">
                                {{ role.role }}
                            </button>
                        </div>
                    </div>
                    <div class="space-y-2">
                        <label class="text-xs text-[var(--text-muted)]">{{ t('kb.restrictionGroups') }}</label>
                        <div class="flex flex-wrap gap-2">
                            <button v-for="group in allGroups" :key="group.id" type="button"
                                    :class="editFolderGroupIds.has(group.id) ? 'border-primary bg-primary/15 text-primary' : 'border-bg-light-accent dark:border-bg-dark-accent text-[var(--text-muted)] hover:border-primary'"
                                    class="px-2 py-1 text-xs rounded border transition-colors"
                                    @click="toggleFolderGroup(group.id)">
                                {{ group.name }}
                            </button>
                            <span v-if="allGroups.length === 0" class="text-xs text-[var(--text-muted)]">–</span>
                        </div>
                    </div>
                    <div class="space-y-2">
                        <label class="text-xs text-[var(--text-muted)]">{{ t('kb.restrictionTags') }}</label>
                        <div class="flex flex-wrap gap-2">
                            <button v-for="tag in allTags" :key="tag.id" type="button"
                                    :class="editFolderTagIds.has(tag.id) ? 'border-primary bg-primary/15 text-primary' : 'border-bg-light-accent dark:border-bg-dark-accent text-[var(--text-muted)] hover:border-primary'"
                                    class="px-2 py-1 text-xs rounded border transition-colors"
                                    @click="toggleFolderTag(tag.id)">
                                {{ tag.name }}
                            </button>
                            <span v-if="allTags.length === 0" class="text-xs text-[var(--text-muted)]">–</span>
                        </div>
                    </div>
                    <p v-if="editFolderRoleIds.size === 0 && editFolderGroupIds.size === 0 && editFolderTagIds.size === 0"
                       class="text-xs text-[var(--text-muted)] italic">{{ t('kb.restrictionsHint') }}</p>
                </div>

                <!-- Tags -->
                <div class="space-y-2 border-t border-bg-light-accent dark:border-bg-dark-accent pt-3">
                    <label class="text-sm font-semibold">{{ t('kb.tags') }}</label>
                    <div class="flex flex-wrap gap-1.5">
                        <span v-for="tag in editFolderTags" :key="tag"
                              class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs bg-[var(--bg-accent)] text-[var(--text)]">
                            {{ tag }}
                            <button type="button" class="hover:text-[var(--error)] cursor-pointer"
                                    @click="editFolderTags = editFolderTags.filter(t => t !== tag)">
                                <font-awesome-icon :icon="['fas', 'xmark']" class="text-[10px]"/>
                            </button>
                        </span>
                    </div>
                    <form class="flex gap-2" @submit.prevent="if (newEditFolderTag.trim()) { editFolderTags.push(newEditFolderTag.trim()); newEditFolderTag = '' }">
                        <TextInput v-model="newEditFolderTag" :placeholder="t('kb.tagPlaceholder')" class="flex-1"/>
                        <SecondaryButton type="submit" :disabled="!newEditFolderTag.trim()">
                            <font-awesome-icon :icon="['fas', 'plus']"/>
                        </SecondaryButton>
                    </form>
                </div>

                <PrimaryButton type="submit">{{ t('common.save') }}</PrimaryButton>
            </form>
        </Modal>

        <!-- Edit File Modal -->
        <Modal v-model="showEditFileModal">
            <h3 class="text-lg font-semibold mb-3">{{ t('kb.editFile') }}</h3>
            <form @submit.prevent="handleEditFile" class="flex flex-col gap-3">
                <TextInput v-model="editFileName" :placeholder="t('kb.fileName')" required/>
                <TextAreaInput v-model="editFileDescription" :placeholder="t('kb.description')"/>

                <!-- Restrictions -->
                <div class="space-y-3 border-t border-bg-light-accent dark:border-bg-dark-accent pt-3">
                    <h3 class="text-sm font-semibold">{{ t('kb.restrictions') }}</h3>
                    <div class="space-y-2">
                        <label class="text-xs text-[var(--text-muted)]">{{ t('kb.restrictionRoles') }}</label>
                        <div class="flex flex-wrap gap-2">
                            <button v-for="role in allRoles" :key="role.id" type="button"
                                    :class="editFileRoleIds.has(role.id) ? 'border-primary bg-primary/15 text-primary' : 'border-bg-light-accent dark:border-bg-dark-accent text-[var(--text-muted)] hover:border-primary'"
                                    class="px-2 py-1 text-xs rounded border transition-colors"
                                    @click="toggleFileRole(role.id)">
                                {{ role.role }}
                            </button>
                        </div>
                    </div>
                    <div class="space-y-2">
                        <label class="text-xs text-[var(--text-muted)]">{{ t('kb.restrictionGroups') }}</label>
                        <div class="flex flex-wrap gap-2">
                            <button v-for="group in allGroups" :key="group.id" type="button"
                                    :class="editFileGroupIds.has(group.id) ? 'border-primary bg-primary/15 text-primary' : 'border-bg-light-accent dark:border-bg-dark-accent text-[var(--text-muted)] hover:border-primary'"
                                    class="px-2 py-1 text-xs rounded border transition-colors"
                                    @click="toggleFileGroup(group.id)">
                                {{ group.name }}
                            </button>
                            <span v-if="allGroups.length === 0" class="text-xs text-[var(--text-muted)]">–</span>
                        </div>
                    </div>
                    <div class="space-y-2">
                        <label class="text-xs text-[var(--text-muted)]">{{ t('kb.restrictionTags') }}</label>
                        <div class="flex flex-wrap gap-2">
                            <button v-for="tag in allTags" :key="tag.id" type="button"
                                    :class="editFileTagIds.has(tag.id) ? 'border-primary bg-primary/15 text-primary' : 'border-bg-light-accent dark:border-bg-dark-accent text-[var(--text-muted)] hover:border-primary'"
                                    class="px-2 py-1 text-xs rounded border transition-colors"
                                    @click="toggleFileTag(tag.id)">
                                {{ tag.name }}
                            </button>
                            <span v-if="allTags.length === 0" class="text-xs text-[var(--text-muted)]">–</span>
                        </div>
                    </div>
                    <p v-if="editFileRoleIds.size === 0 && editFileGroupIds.size === 0 && editFileTagIds.size === 0"
                       class="text-xs text-[var(--text-muted)] italic">{{ t('kb.restrictionsHint') }}</p>
                </div>

                <!-- Tags -->
                <div class="space-y-2 border-t border-bg-light-accent dark:border-bg-dark-accent pt-3">
                    <label class="text-sm font-semibold">{{ t('kb.tags') }}</label>
                    <div class="flex flex-wrap gap-1.5">
                        <span v-for="tag in editFileTags" :key="tag"
                              class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs bg-[var(--bg-accent)] text-[var(--text)]">
                            {{ tag }}
                            <button type="button" class="hover:text-[var(--error)] cursor-pointer"
                                    @click="editFileTags = editFileTags.filter(t => t !== tag)">
                                <font-awesome-icon :icon="['fas', 'xmark']" class="text-[10px]"/>
                            </button>
                        </span>
                    </div>
                    <form class="flex gap-2" @submit.prevent="if (newEditFileTag.trim()) { editFileTags.push(newEditFileTag.trim()); newEditFileTag = '' }">
                        <TextInput v-model="newEditFileTag" :placeholder="t('kb.tagPlaceholder')" class="flex-1"/>
                        <SecondaryButton type="submit" :disabled="!newEditFileTag.trim()">
                            <font-awesome-icon :icon="['fas', 'plus']"/>
                        </SecondaryButton>
                    </form>
                </div>

                <PrimaryButton type="submit">{{ t('common.save') }}</PrimaryButton>
            </form>
        </Modal>

        <!-- Delete Folder Confirmation -->
        <Modal v-model="showDeleteFolderModal">
            <h3 class="text-lg font-semibold mb-3">{{ t('kb.deleteFolder') }}</h3>
            <p class="mb-4">{{ t('kb.deleteFolderConfirm') }}</p>
            <div class="flex gap-2 justify-end">
                <SecondaryButton @click="showDeleteFolderModal = false">{{ t('common.cancel') }}</SecondaryButton>
                <DeleteButton :label="t('common.delete')" @click="handleDeleteFolder">
                    {{ t('common.delete') }}
                </DeleteButton>
            </div>
        </Modal>

        <!-- Delete File Confirmation -->
        <Modal v-model="showDeleteFileModal">
            <h3 class="text-lg font-semibold mb-3">{{ t('kb.deleteFile') }}</h3>
            <p class="mb-4">{{ t('kb.deleteFileConfirm') }}</p>
            <div class="flex gap-2 justify-end">
                <SecondaryButton @click="showDeleteFileModal = false">{{ t('common.cancel') }}</SecondaryButton>
                <DeleteButton :label="t('common.delete')" @click="handleDeleteFile">
                    {{ t('common.delete') }}
                </DeleteButton>
            </div>
        </Modal>
    </ViewContent>
</template>
