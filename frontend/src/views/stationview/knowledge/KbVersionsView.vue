/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import DiffView from '@/components/display/DiffView.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import {useSession} from '@/composables/useSession'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {knowledgeBase} from '@/api'
import {formatDateTime} from '@/util/format'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {KbFile, KbFileVersion} from '@/api/knowledgeBase'
import {ContentMode} from '@/api/news'
import {STATION_KB_ROUTES, type KbRoutes} from './knowledgebaseview/useKbNavigation'

const props = defineProps<{
    /** The pages this knowledge base is mounted on, which differ when an association opens its own. */
    routes?: KbRoutes
}>()

const routes = computed(() => props.routes ?? STATION_KB_ROUTES)

const {t} = useI18n()
const router = useRouter()
const route = useRoute()
const {loaded} = useSession()

const file = ref<KbFile | null>(null)
const versions = ref<KbFileVersion[]>([])

const selectedVersion = ref<KbFileVersion | null>(null)
const loadingVersion = ref(false)

const fileId = computed(() => Number(route.params.id))

/**
 * Restoring an old version is offered only for an article written as text.
 *
 * <p>History stores patches against the stored body, which for an article built from blocks is a
 * projection of them. The history therefore reads and diffs correctly, but restoring one would put
 * the projection back as the body while the blocks stayed where they are: the article would say one
 * thing and be built from another. Reading an old version is still offered, because that part works.
 */
const canRevert = computed(() => file.value?.contentMode !== ContentMode.RICH)

const {loading, error, reload: loadData} = useAsyncLoader(async () => {
    file.value = (await knowledgeBase.getFile(fileId.value)).file
    versions.value = await knowledgeBase.listVersions(fileId.value)
}, {autoLoad: false})

const {
    show: showRevertModal,
    request: requestRevert,
    confirm: handleRevert,
} = useConfirmAction<KbFileVersion>({
    onConfirm: v => knowledgeBase.revertToVersion(fileId.value, v.version),
    onSuccess: () => { router.push({name: routes.value.file, params: {id: fileId.value}}) },
    error,
})

async function viewVersion(version: KbFileVersion) {
    loadingVersion.value = true
    try {
        selectedVersion.value = await knowledgeBase.getVersion(fileId.value, version.version)
    } catch {
        error.value = t('common.error')
    } finally {
        loadingVersion.value = false
    }
}

watch(loaded, (isLoaded) => {
    if (isLoaded) loadData()
}, {immediate: true})
</script>

<template>
    <ViewContent
        :title="t('pages.kb-versions.title')"
        :subtitle="t('pages.kb-versions.subtitle')"
    >
        <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>
        <Spinner v-if="loading"/>

        <template v-else>
            <div class="flex items-center gap-2 mb-4">
                <SecondaryButton @click="router.push({name: routes.file, params: {id: fileId}})">
                    <font-awesome-icon :icon="['fas', 'chevron-left']"/>
                    {{ t('common.back') }}
                </SecondaryButton>
                <PageHeader class="text-xl font-bold">{{ t('kb.versions') }} - {{ file?.name }}</PageHeader>
            </div>

            <div v-if="versions.length === 0" class="text-[var(--text-muted)] text-center py-8">
                {{ t('kb.noContent') }}
            </div>

            <div v-else class="flex flex-col gap-3">
                <NeutralContainer
                    v-for="version in versions"
                    :key="version.id"
                    data-testid="kb-version"
                    :data-version="version.version"
                    class="flex flex-col sm:flex-row sm:items-center gap-3"
                >
                    <div class="flex-1">
                        <span class="font-semibold">{{ t('kb.version') }} {{ version.version }}</span>
                        <span class="text-sm text-[var(--text-muted)] ml-2">
                            {{ formatDateTime(version.createdAt) }}
                        </span>
                        <span v-if="version.createdByName" class="text-sm text-[var(--text-muted)] ml-2">
                            &mdash; {{ version.createdByName }}
                        </span>
                    </div>
                    <div class="flex gap-2">
                        <SecondaryButton @click="viewVersion(version)">
                            <font-awesome-icon :icon="['fas', 'eye']"/>
                        </SecondaryButton>
                        <PrimaryButton v-if="canRevert" @click="requestRevert(version)">
                            {{ t('kb.revert') }}
                        </PrimaryButton>
                    </div>
                </NeutralContainer>
            </div>

            <Alert v-if="!canRevert" variant="info" class="mt-3">{{ t('kb.revertUnavailableForBlocks') }}</Alert>

            <!-- Version content view -->
            <div v-if="selectedVersion" class="mt-6">
                <SectionHeader class="text-lg font-semibold mb-2">
                    {{ t('kb.version') }} {{ selectedVersion.version }} - {{ t('kb.content') }}
                </SectionHeader>
                <Spinner v-if="loadingVersion"/>

                <!-- First version: show full content -->
                <template v-else-if="selectedVersion.isFull">
                    <p class="text-sm text-[var(--text-muted)] mb-2">{{ t('kb.initialVersion') }}</p>
                    <NeutralContainer>
                        <pre class="whitespace-pre-wrap text-sm font-mono">{{ selectedVersion.patch }}</pre>
                    </NeutralContainer>
                </template>

                <!-- Subsequent versions: colored diff -->
                <template v-else>
                    <NeutralContainer class="!p-0 overflow-hidden">
                        <DiffView :patch="selectedVersion.patch" show-line-numbers/>
                    </NeutralContainer>
                </template>
            </div>
        </template>

        <!-- Revert Confirmation -->
        <Modal v-model="showRevertModal">
            <template #title>{{ t('kb.revert') }}</template>
            <p class="mb-4">{{ t('kb.revertConfirm') }}</p>
            <div class="flex gap-2 justify-end">
                <SecondaryButton @click="showRevertModal = false">{{ t('common.cancel') }}</SecondaryButton>
                <PrimaryButton @click="handleRevert">{{ t('kb.revert') }}</PrimaryButton>
            </div>
        </Modal>
    </ViewContent>
</template>
