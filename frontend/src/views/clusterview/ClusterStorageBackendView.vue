/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {RouterLink} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import StorageApplyConfirmModal from '@/components/storage/StorageApplyConfirmModal.vue'
import StorageBackendForm from '@/components/storage/StorageBackendForm.vue'
import StorageBackendHistory from '@/components/storage/StorageBackendHistory.vue'
import StorageBackendSummaryCard from '@/components/storage/StorageBackendSummaryCard.vue'
import StoragePlacementTable from '@/components/storage/StoragePlacementTable.vue'
import ClusterStoragePolicyPanel from '@/views/clusterview/clusterstoragebackendview/ClusterStoragePolicyPanel.vue'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useStorageBackendEditor} from '@/composables/useStorageBackendEditor'
import {
    ClusterBackendReach,
    type ClusterBackendReachName,
    applyClusterBackend,
    dropClusterBackend,
    getClusterBackend,
    getClusterPlacements,
    getClusterStorageAudit,
    moveStationStorage,
    probeClusterBackend,
    probeClusterBackendConfig,
    setClusterBackendPolicy,
} from '@/api/clusterStorageBackend'
import type {AuditEntryResponse, PlacementResponse, PolicyResponse} from '@/api/generated/schema'

const {t} = useI18n()

const policy = ref<PolicyResponse | null>(null)
const placements = ref<PlacementResponse[]>([])
const auditEntries = ref<AuditEntryResponse[]>([])
const movingUid = ref<string | null>(null)
const reach = ref<ClusterBackendReachName>(ClusterBackendReach.NONE)
const locked = ref(false)

const editor = useStorageBackendEditor({
    probeSaved: probeClusterBackend,
    probeTyped: (request) => probeClusterBackendConfig(request),
    reload: () => loadAll(),
    reloadHistory: async () => { auditEntries.value = await getClusterStorageAudit() },
}, 'S3')
const {selectedType, s3, smb, sftp, savedOutcome, typedOutcome, success, pending, probing, saving, failure} = editor

const {loading, failure: loadFailure, reload: loadAll} = useAsyncLoader(async () => {
    policy.value = await getClusterBackend()
    reach.value = policy.value.reach
    locked.value = policy.value.locked
    editor.seed(policy.value.backend, 'S3')
    placements.value = await getClusterPlacements()
    auditEntries.value = await getClusterStorageAudit()
})

const hasBackend = computed(() => policy.value?.backend != null)

const summaryLines = computed(() => {
    const backend = policy.value?.backend
    return [backend ? t('clusterStorageBackend.summary.current', {type: backend.type}) : t('clusterStorageBackend.summary.none')]
})

function savePolicy() {
    return editor.perform(async () => {
        await setClusterBackendPolicy({reach: reach.value, locked: locked.value})
        return t('clusterStorageBackend.feedback.policySaved')
    })
}

function saveBackend() {
    const request = editor.currentRequest()
    return editor.perform(async () => {
        await applyClusterBackend(request)
        return t('clusterStorageBackend.feedback.backendSaved')
    })
}

function drop() {
    editor.askFirst(t('clusterStorageBackend.confirm.drop'), async () => {
        await dropClusterBackend()
        return t('clusterStorageBackend.feedback.dropped')
    })
}

function move(stationUid: string) {
    const name = placements.value.find(placement => placement.stationUid === stationUid)?.name ?? ''
    editor.askFirst(t('clusterStorageBackend.confirm.move', {station: name}), async () => {
        movingUid.value = stationUid
        try {
            const result = await moveStationStorage(stationUid)
            return t('clusterStorageBackend.feedback.moved', {copied: result.copied, deleted: result.deleted})
        } finally {
            movingUid.value = null
        }
    })
}
</script>

<template>
    <ViewContent
        :title="t('pages.cluster-storage-backend.title')"
        :subtitle="t('pages.cluster-storage-backend.subtitle')"
    >
        <div class="space-y-6">
            <div class="flex justify-end">
                <RouterLink :to="{name: 'cluster-storage'}" class="text-sm underline">
                    {{ t('clusterStorageBackend.backToRoom') }}
                </RouterLink>
            </div>

            <FailureAlert :failure="loadFailure"/>
            <FailureAlert :failure="failure"/>
            <Alert v-if="success" variant="success">{{ success }}</Alert>

            <Spinner v-if="loading" size="lg"/>

            <template v-else>
                <StorageBackendSummaryCard
                    :lines="summaryLines"
                    :can-probe="hasBackend"
                    :probing="probing"
                    :probe-outcome="savedOutcome"
                    @probe="editor.probeSaved"/>

                <ClusterStoragePolicyPanel v-model:reach="reach" v-model:locked="locked"
                                           :saving="saving" :has-backend="hasBackend"
                                           @save="savePolicy" @drop="drop"/>

                <StorageBackendForm
                    v-model:selected-type="selectedType"
                    v-model:s3="s3"
                    v-model:smb="smb"
                    v-model:sftp="sftp"
                    i18n-prefix="clusterStorageBackend"
                    :types="['S3', 'SMB', 'SFTP']"
                    :probing="probing"
                    :saving="saving"
                    :probe-outcome="typedOutcome"
                    @probe-config="editor.probeTyped"
                    @apply="saveBackend"
                />

                <NeutralContainer class="space-y-3">
                    <SubHeader>{{ t('clusterStorageBackend.placements.title') }}</SubHeader>
                    <MutedText tag="p" size="sm">{{ t('clusterStorageBackend.placements.hint') }}</MutedText>
                    <StoragePlacementTable :placements="placements" :moving-uid="movingUid" @move="move"/>
                </NeutralContainer>

                <StorageBackendHistory :entries="auditEntries"/>
            </template>
        </div>

        <StorageApplyConfirmModal
            :title="t('clusterStorageBackend.confirm.title')"
            :body="pending?.body ?? null"
            :saving="saving"
            @confirm="editor.confirmPending"
            @cancel="editor.cancelPending"/>
    </ViewContent>
</template>
