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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import {useSession} from '@/composables/useSession'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {knowledgeBase} from '@/api'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {KbFile, KbFileVersion, KbVersionResponse} from '@/api/generated/schema'
import {ContentMode} from '@/api/news'
import {STATION_KB_ROUTES, type KbRoutes} from './knowledgebaseview/useKbNavigation'
import KbVersionList from './kbversionsview/KbVersionList.vue'

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
const versions = ref<KbVersionResponse[]>([])

const selectedVersion = ref<KbFileVersion | null>(null)

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

/**
 * The file's name before the word, because this page belongs to one file and "Versionen" alone is
 * true of every file in the wiki. It is also what the browser tab reads, and two tabs of versions
 * open on two files are otherwise the same tab twice.
 */
const pageTitle = computed(() => (file.value
    ? `${file.value.name} ${t('kb.versions')}`
    : t('pages.kb-versions.title')))

const {loading, failure, reload: loadData} = useAsyncLoader(async () => {
    file.value = (await knowledgeBase.getFile(fileId.value)).file
    versions.value = await knowledgeBase.listVersions(fileId.value)
}, {autoLoad: false})

const {
    show: showRevertModal,
    request: requestRevert,
    confirm: handleRevert,
} = useConfirmAction<KbVersionResponse>({
    onConfirm: v => knowledgeBase.revertToVersion(fileId.value, v.version),
    onSuccess: () => { router.push({name: routes.value.file, params: {id: fileId.value}}) },
    failure,
})

const versionAsked = ref(0)

const {loading: loadingVersion, failure: versionFailure, reload: fetchVersion} = useAsyncLoader(async (isCurrent) => {
    const found = await knowledgeBase.getVersion(fileId.value, versionAsked.value)
    if (isCurrent()) selectedVersion.value = found
}, {autoLoad: false, errorMessageKey: 'kb.versionsLoadFailed'})

/** Opens one version, saying so above the page where it cannot be read. */
async function viewVersion(version: KbVersionResponse) {
    versionAsked.value = version.version
    await fetchVersion()
    if (versionFailure.value) failure.value = versionFailure.value
}

watch(loaded, (isLoaded) => {
    if (isLoaded) loadData()
}, {immediate: true})
</script>

<template>
    <ViewContent :title="pageTitle" :subtitle="t('pages.kb-versions.subtitle')">
        <FailureAlert :failure="failure" class="mb-4"/>
        <Spinner v-if="loading"/>

        <template v-else>
            <div class="flex items-center gap-2 mb-4">
                <SecondaryButton @click="router.push({name: routes.file, params: {id: fileId}})">
                    <font-awesome-icon :icon="['fas', 'chevron-left']"/>
                    {{ t('common.back') }}
                </SecondaryButton>
                <PageHeader class="text-xl font-bold">{{ t('kb.versions') }} - {{ file?.name }}</PageHeader>
            </div>

            <KbVersionList
                :versions="versions"
                :can-revert="canRevert"
                @view="viewVersion"
                @revert="requestRevert"
            />

            <Alert v-if="!canRevert" variant="info" class="mt-3">{{ t('kb.revertUnavailableForBlocks') }}</Alert>

            <div v-if="selectedVersion" class="mt-6">
                <SectionHeader class="text-lg font-semibold mb-2">
                    {{ t('kb.version') }} {{ selectedVersion.version }} - {{ t('kb.content') }}
                </SectionHeader>
                <Spinner v-if="loadingVersion"/>

                <template v-else-if="selectedVersion.isFull">
                    <p class="text-sm text-[var(--text-muted)] mb-2">{{ t('kb.initialVersion') }}</p>
                    <NeutralContainer>
                        <pre class="whitespace-pre-wrap text-sm font-mono">{{ selectedVersion.patch }}</pre>
                    </NeutralContainer>
                </template>

                <template v-else>
                    <NeutralContainer class="!p-0 overflow-hidden">
                        <DiffView :patch="selectedVersion.patch" show-line-numbers/>
                    </NeutralContainer>
                </template>
            </div>
        </template>

        <Modal v-model="showRevertModal">
            <template #title>{{ t('kb.revert') }}</template>
            <p class="mb-4">{{ t('kb.revertConfirm') }}</p>
            <ButtonRow pair align="end">
                <SecondaryButton @click="showRevertModal = false">{{ t('common.cancel') }}</SecondaryButton>
                <PrimaryButton @click="handleRevert">{{ t('kb.revert') }}</PrimaryButton>
            </ButtonRow>
        </Modal>
    </ViewContent>
</template>
