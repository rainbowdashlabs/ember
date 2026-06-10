/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import DragList from '@/components/input/DragList.vue'
import {
    listPages,
    createPage,
    deletePage,
    duplicatePage,
    setPublished,
    setLandingPage,
    type StationPage,
} from '@/api/pageManage'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const router = useRouter()
const {hasPermission} = useSession()

const canEdit = computed(() => hasPermission(StationPermission.PAGE_EDIT))
const canManage = computed(() => hasPermission(StationPermission.PAGE_MANAGER))

const pages = ref<StationPage[]>([])
const loading = ref(true)
const error = ref('')

// Create modal
const showCreateModal = ref(false)
const newTitle = ref('')
const newParentId = ref<string>('')

// Delete modal
const showDeleteModal = ref(false)
const deleteTarget = ref<StationPage | null>(null)

// Landing page
const landingPageId = ref<number | null>(null)

interface FlatPageEntry {
    page: StationPage
    depth: number
}

const flatPages = computed<FlatPageEntry[]>(() => {
    const result: FlatPageEntry[] = []
    const pageMap = new Map<number | null, StationPage[]>()

    for (const p of pages.value) {
        const key = p.parentId
        if (!pageMap.has(key)) pageMap.set(key, [])
        pageMap.get(key)!.push(p)
    }

    function walk(parentId: number | null, depth: number) {
        const children = pageMap.get(parentId) ?? []
        children
            .sort((a, b) => a.sortOrder - b.sortOrder)
            .forEach(p => {
                result.push({page: p, depth})
                walk(p.id, depth + 1)
            })
    }

    walk(null, 0)
    return result
})

const topLevelPages = computed(() =>
    pages.value.filter(p => p.parentId == null),
)

async function loadData() {
    loading.value = true
    error.value = ''
    try {
        const result = await listPages()
        pages.value = result.pages
        landingPageId.value = result.landingPageId
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
}

function openCreateModal() {
    newTitle.value = ''
    newParentId.value = ''
    showCreateModal.value = true
}

async function confirmCreate() {
    if (!newTitle.value.trim()) return
    try {
        const parentIdNum = newParentId.value ? Number(newParentId.value) : null
        await createPage(newTitle.value.trim(), parentIdNum)
        showCreateModal.value = false
        await loadData()
    } catch {
        error.value = t('common.error')
    }
}

function requestDelete(page: StationPage) {
    deleteTarget.value = page
    showDeleteModal.value = true
}

async function confirmDelete() {
    if (!deleteTarget.value) return
    try {
        await deletePage(deleteTarget.value.id)
        showDeleteModal.value = false
        deleteTarget.value = null
        await loadData()
    } catch {
        error.value = t('common.error')
    }
}

async function onDuplicate(page: StationPage) {
    try {
        await duplicatePage(page.id)
        await loadData()
    } catch {
        error.value = t('common.error')
    }
}

async function onTogglePublish(page: StationPage) {
    try {
        await setPublished(page.id, !page.published)
        await loadData()
    } catch {
        error.value = t('common.error')
    }
}

async function onSetLandingPage(page: StationPage) {
    try {
        const newLandingId = landingPageId.value === page.id ? null : page.id
        await setLandingPage(newLandingId)
        landingPageId.value = newLandingId
    } catch {
        error.value = t('common.error')
    }
}

function navigateToEdit(page: StationPage) {
    router.push({name: 'page-editor', params: {id: page.id}})
}

function onReorder(fromIndex: number, toIndex: number) {
    const items = [...flatPages.value]
    const [moved] = items.splice(fromIndex, 1)
    items.splice(toIndex, 0, moved)
    // Update local state (sorting persists via individual saves)
    pages.value = items.map((entry, i) => ({...entry.page, sortOrder: i}))
}

onMounted(() => loadData())
</script>

<template>
    <ViewContent>
        <div class="space-y-6">
            <!-- Header -->
            <div class="flex items-center justify-between flex-wrap gap-2">
                <SectionHeader>{{ t('stationPages.title') }}</SectionHeader>
                <PrimaryButton v-if="canEdit" @click="openCreateModal">
                    <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
                    {{ t('stationPages.createPage') }}
                </PrimaryButton>
            </div>

            <Spinner v-if="loading" size="lg"/>
            <Alert v-if="error" variant="error">{{ error }}</Alert>

            <EmptyState v-if="!loading && flatPages.length === 0">
                {{ t('stationPages.empty') }}
            </EmptyState>

            <!-- Pages list -->
            <DragList
                v-if="!loading && flatPages.length > 0"
                :items="flatPages"
                :key-fn="(entry: FlatPageEntry) => entry.page.id"
                @reorder="onReorder"
            >
                <template #default="{item}: {item: FlatPageEntry}">
                    <NeutralContainer
                        class="flex items-center gap-3 my-1"
                        :style="{marginLeft: `${item.depth * 1.5}rem`}"
                    >
                        <!-- Drag handle -->
                        <div class="cursor-grab active:cursor-grabbing text-[var(--text-muted)] shrink-0">
                            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="h-4 w-4"/>
                        </div>

                        <!-- Title & badges -->
                        <div class="flex-1 min-w-0 flex items-center gap-2 flex-wrap">
                            <span class="font-medium truncate">{{ item.page.title }}</span>
                            <span class="text-xs text-[var(--text-muted)]">/{{ item.page.slug }}</span>
                            <SuccessBadge v-if="item.page.published">
                                {{ t('stationPages.published') }}
                            </SuccessBadge>
                            <SecondaryBadge v-else>
                                {{ t('stationPages.draft') }}
                            </SecondaryBadge>
                            <font-awesome-icon
                                v-if="landingPageId === item.page.id"
                                :icon="['fas', 'star']"
                                class="h-4 w-4 text-info-accent"
                                :title="t('stationPages.landingPage')"
                            />
                        </div>

                        <!-- Actions -->
                        <div class="flex items-center gap-1 shrink-0">
                            <EditButton
                                v-if="canEdit"
                                @click="navigateToEdit(item.page)"
                            />
                            <IconButton
                                v-if="canEdit"
                                :icon="['fas', 'clone']"
                                :label="t('stationPages.duplicate')"
                                class="text-[var(--text-muted)] hover:text-[var(--text)]"
                                @click="onDuplicate(item.page)"
                            />
                            <IconButton
                                v-if="canManage"
                                :icon="['fas', item.page.published ? 'eye-slash' : 'eye']"
                                :label="item.page.published ? t('stationPages.unpublish') : t('stationPages.publish')"
                                class="text-[var(--text-muted)] hover:text-[var(--text)]"
                                @click="onTogglePublish(item.page)"
                            />
                            <IconButton
                                v-if="canManage"
                                :icon="['fas', 'star']"
                                :label="t('stationPages.setLandingPage')"
                                :disabled="((!item.page.published || item.page.parentId != null) && landingPageId !== item.page.id)"
                                :class="landingPageId === item.page.id ? 'text-info-accent' : 'text-[var(--text-muted)] hover:text-info-accent'"
                                @click="onSetLandingPage(item.page)"
                            />
                            <DeleteButton
                                v-if="canManage"
                                @click="requestDelete(item.page)"
                            />
                        </div>
                    </NeutralContainer>
                </template>
            </DragList>

            <!-- Create modal -->
            <Modal v-model="showCreateModal">
                <div class="space-y-4">
                    <SectionHeader>{{ t('stationPages.createPage') }}</SectionHeader>
                    <div>
                        <FormLabel>{{ t('stationPages.editor.titleLabel') }}</FormLabel>
                        <TextInput
                            v-model="newTitle"
                            :placeholder="t('stationPages.editor.titlePlaceholder')"
                        />
                    </div>
                    <div>
                        <FormLabel>{{ t('stationPages.editor.parent') }}</FormLabel>
                        <SelectInput v-model="newParentId">
                            <option value="">{{ t('stationPages.editor.noParent') }}</option>
                            <option
                                v-for="p in topLevelPages"
                                :key="p.id"
                                :value="String(p.id)"
                            >
                                {{ p.title }}
                            </option>
                        </SelectInput>
                    </div>
                    <div class="flex justify-end gap-3">
                        <SecondaryButton @click="showCreateModal = false">{{ t('common.cancel') }}</SecondaryButton>
                        <PrimaryButton :disabled="!newTitle.trim()" @click="confirmCreate">{{ t('common.create') }}</PrimaryButton>
                    </div>
                </div>
            </Modal>

            <!-- Delete modal -->
            <Modal v-model="showDeleteModal">
                <div class="space-y-4">
                    <p>{{ t('stationPages.deleteConfirm', {title: deleteTarget?.title}) }}</p>
                    <div class="flex justify-end gap-3">
                        <SecondaryButton @click="showDeleteModal = false">{{ t('common.cancel') }}</SecondaryButton>
                        <ErrorButton @click="confirmDelete">{{ t('common.delete') }}</ErrorButton>
                    </div>
                </div>
            </Modal>
        </div>
    </ViewContent>
</template>
