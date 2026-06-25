/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PagesListContent from './pageslistview/PagesListContent.vue'
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
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {t} = useI18n()
const router = useRouter()
const {hasPermission} = useSession()

const canEdit = computed(() => hasPermission(StationPermission.PAGE_EDIT))
const canManage = computed(() => hasPermission(StationPermission.PAGE_MANAGER))

const pages = ref<StationPage[]>([])

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

const {loading, error, reload} = useAsyncLoader(async () => {
    const result = await listPages()
    pages.value = result.pages
    landingPageId.value = result.landingPageId
})

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
        await reload()
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
        await reload()
    } catch {
        error.value = t('common.error')
    }
}

async function onDuplicate(page: StationPage) {
    try {
        await duplicatePage(page.id)
        await reload()
    } catch {
        error.value = t('common.error')
    }
}

async function onTogglePublish(page: StationPage) {
    try {
        await setPublished(page.id, !page.published)
        await reload()
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

</script>

<template>
    <ViewContent>
        <PagesListContent
            v-model:show-create-modal="showCreateModal"
            v-model:new-title="newTitle"
            v-model:new-parent-id="newParentId"
            v-model:show-delete-modal="showDeleteModal"
            :can-edit="canEdit"
            :can-manage="canManage"
            :loading="loading"
            :error="error"
            :flat-pages="flatPages"
            :landing-page-id="landingPageId"
            :top-level-pages="topLevelPages"
            :delete-target="deleteTarget"
            @open-create="openCreateModal"
            @confirm-create="confirmCreate"
            @reorder="onReorder"
            @edit="navigateToEdit"
            @duplicate="onDuplicate"
            @toggle-publish="onTogglePublish"
            @set-landing="onSetLandingPage"
            @request-delete="requestDelete"
            @confirm-delete="confirmDelete"
        />
    </ViewContent>
</template>
