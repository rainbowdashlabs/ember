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
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ShareLinkPanel from '@/components/public/ShareLinkPanel.vue'
import PagesListContent from './pageslistview/PagesListContent.vue'
import PageVisibilityModal from './pageslistview/PageVisibilityModal.vue'
import {
    listPages,
    createPage,
    deletePage,
    duplicatePage,
    setVisibility,
    getPageShareLink,
    replacePageShareLink,
    setLandingPage,
    type PageVisibilityName,
    type StationPage,
} from '@/api/pageManage'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {moveWithin} from '@/util/reorder'

const {t} = useI18n()
const router = useRouter()
const {hasPermission} = useSession()

const canEdit = computed(() => hasPermission(StationPermission.PAGE_EDIT))
const canManage = computed(() => hasPermission(StationPermission.PAGE_MANAGER))

const pages = ref<StationPage[]>([])

const showCreateModal = ref(false)
const newTitle = ref('')
const newParentId = ref<string>('')

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

const {
    show: showDeleteModal,
    target: deleteTarget,
    requestDelete,
    confirm: confirmDelete,
} = useConfirmDelete<StationPage>({
    onDelete: p => deletePage(p.id),
    onSuccess: () => reload(),
    error,
})

async function onDuplicate(page: StationPage) {
    try {
        await duplicatePage(page.id)
        await reload()
    } catch {
        error.value = t('common.error')
    }
}

const visibilityPage = ref<StationPage | null>(null)
const visibilityError = ref('')

const sharePage = ref<StationPage | null>(null)
const shareOpen = ref(false)
const shareToken = ref<string | null>(null)
const shareError = ref('')
const shareBusy = ref(false)

async function onChooseVisibility(visibility: PageVisibilityName) {
    const page = visibilityPage.value
    if (!page) return
    visibilityError.value = ''
    try {
        await setVisibility(page.id, visibility)
        visibilityPage.value = null
        await reload()
    } catch (e) {
        const said = (e as {response?: {data?: {message?: string}}})?.response?.data?.message
        visibilityError.value = said || t('common.error')
    }
}

async function onShareLink(page: StationPage) {
    sharePage.value = page
    shareError.value = ''
    shareToken.value = null
    shareOpen.value = true
    try {
        shareToken.value = await getPageShareLink(page.id)
    } catch {
        shareError.value = t('common.error')
    }
}

async function onReplaceShareLink() {
    const page = sharePage.value
    if (!page) return
    shareBusy.value = true
    shareError.value = ''
    try {
        shareToken.value = await replacePageShareLink(page.id, shareToken.value)
    } catch {
        shareError.value = t('shareLink.replaceConflict')
    } finally {
        shareBusy.value = false
    }
}

/**
 * Makes the page's first link.
 *
 * <p>A page carries no link until somebody asks for one, so the dialog opens on a page that has
 * none and this is the way out of it. Replacing nothing is what making the first one is.
 */
async function onCreateShareLink() {
    const page = sharePage.value
    if (!page) return
    shareBusy.value = true
    shareError.value = ''
    try {
        shareToken.value = await replacePageShareLink(page.id, null)
    } catch {
        shareError.value = t('shareLink.createFailed')
    } finally {
        shareBusy.value = false
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
    pages.value = moveWithin(flatPages.value, fromIndex, toIndex)
        .map((entry, i) => ({...entry.page, sortOrder: i}))
}

</script>

<template>
    <ViewContent :title="t('pages.pages-list.title')" :subtitle="t('pages.pages-list.subtitle')">
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
            @change-visibility="(p: StationPage) => { visibilityPage = p; visibilityError = '' }"
            @share-link="onShareLink"
            @set-landing="onSetLandingPage"
            @request-delete="requestDelete"
            @confirm-delete="confirmDelete"
        />

        <PageVisibilityModal
            :page="visibilityPage"
            :error="visibilityError"
            @choose="onChooseVisibility"
            @close="visibilityPage = null"
        />

        <Modal v-model="shareOpen">
            <div class="space-y-4">
                <SubHeader>{{ t('stationPages.shareLink') }}</SubHeader>
                <ShareLinkPanel
                    v-if="shareToken"
                    :path="`/s/${shareToken}`"
                    :busy="shareBusy"
                    :error="shareError"
                    replaceable
                    @replace="onReplaceShareLink"
                />
                <Alert v-else-if="shareError" variant="error">{{ shareError }}</Alert>
                <div v-else class="space-y-3">
                    <MutedText tag="p" size="sm">{{ t('shareLink.none') }}</MutedText>
                    <ButtonRow align="end">
                        <PrimaryButton :disabled="shareBusy" :icon="['fas', 'link']" @click="onCreateShareLink">
                            {{ t('shareLink.create') }}
                        </PrimaryButton>
                    </ButtonRow>
                </div>
            </div>
        </Modal>
    </ViewContent>
</template>
