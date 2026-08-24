/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {RouterLink} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import StorageBackendForm from '@/components/storage/StorageBackendForm.vue'
import StoragePlacementTable from '@/components/storage/StoragePlacementTable.vue'
import ClusterStoragePolicyPanel from '@/views/clusterview/clusterstoragebackendview/ClusterStoragePolicyPanel.vue'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {apiErrorMessage} from '@/util/apiError'
import {
    ClusterBackendReach,
    type ClusterBackendPolicy,
    type ClusterBackendReachName,
    type StoragePlacement,
    applyClusterBackend,
    dropClusterBackend,
    getClusterBackend,
    getClusterPlacements,
    moveStationStorage,
    probeClusterBackend,
    probeClusterBackendConfig,
    setClusterBackendPolicy,
} from '@/api/clusterStorageBackend'
import type {ProbeResult, S3Request, SftpRequest, SmbRequest, StationBackendRequest} from '@/api/storageBackend'
import {newS3, newSftp, newSmb, s3FormFrom, sftpFormFrom, smbFormFrom} from '@/util/storageBackendForm'

const {t} = useI18n()

const loading = ref(true)
const error = ref('')
const success = ref('')
const policy = ref<ClusterBackendPolicy | null>(null)
const placements = ref<StoragePlacement[]>([])
const movingUid = ref<string | null>(null)

const reach = ref<ClusterBackendReachName>(ClusterBackendReach.NONE)
const locked = ref(false)
const selectedType = ref<'LOCAL' | 'S3' | 'SMB' | 'SFTP'>('S3')
const s3 = ref<S3Request>(newS3())
const smb = ref<SmbRequest>(newSmb())
const sftp = ref<SftpRequest>(newSftp())
const probeOutcome = ref<ProbeResult | null>(null)

const hasBackend = computed(() => policy.value?.backend != null)

onMounted(loadAll)

async function loadAll() {
    loading.value = true
    error.value = ''
    try {
        policy.value = await getClusterBackend()
        reach.value = policy.value.reach
        locked.value = policy.value.locked
        seedForm()
        placements.value = await getClusterPlacements()
    } catch (e) {
        error.value = apiErrorMessage(e) || t('clusterStorageBackend.errors.loadFailed')
    } finally {
        loading.value = false
    }
}

function seedForm() {
    const summary = policy.value?.backend
    if (!summary) return
    selectedType.value = summary.type
    if (summary.type === 'S3') s3.value = s3FormFrom(summary)
    if (summary.type === 'SMB') smb.value = smbFormFrom(summary)
    if (summary.type === 'SFTP') sftp.value = sftpFormFrom(summary)
}

function currentRequest(): StationBackendRequest | null {
    if (selectedType.value === 'S3') return s3.value
    if (selectedType.value === 'SMB') return smb.value
    if (selectedType.value === 'SFTP') return sftp.value
    return null
}

const {running: probing, run: runProbe} = useAsyncAction(async (call: () => Promise<ProbeResult>) => {
    try {
        probeOutcome.value = await call()
    } catch (e) {
        probeOutcome.value = {
            healthy: false,
            error: apiErrorMessage(e) || t('clusterStorageBackend.errors.probeFailed'),
            checkedAt: new Date().toISOString(),
        }
    }
})

function probeConfig() {
    const request = currentRequest()
    probeOutcome.value = null
    if (request) runProbe(() => probeClusterBackendConfig(request))
}

function probeLive() {
    probeOutcome.value = null
    if (hasBackend.value) runProbe(() => probeClusterBackend())
}

const {running: saving, run: runAction} = useAsyncAction(async (act: () => Promise<string>) => {
    error.value = ''
    success.value = ''
    try {
        success.value = await act()
        await loadAll()
    } catch (e) {
        error.value = apiErrorMessage(e) || t('clusterStorageBackend.errors.saveFailed')
    }
})

function savePolicy() {
    return runAction(async () => {
        await setClusterBackendPolicy({reach: reach.value, locked: locked.value})
        return t('clusterStorageBackend.feedback.policySaved')
    })
}

function saveBackend() {
    const request = currentRequest()
    if (!request) return
    return runAction(async () => {
        await applyClusterBackend(request)
        return t('clusterStorageBackend.feedback.backendSaved')
    })
}

function drop() {
    return runAction(async () => {
        await dropClusterBackend()
        return t('clusterStorageBackend.feedback.dropped')
    })
}

function move(stationUid: string) {
    movingUid.value = stationUid
    return runAction(async () => {
        const result = await moveStationStorage(stationUid)
        movingUid.value = null
        return t('clusterStorageBackend.feedback.moved', {copied: result.copied, deleted: result.deleted})
    }).finally(() => (movingUid.value = null))
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

            <Alert v-if="error" variant="error">{{ error }}</Alert>
            <Alert v-if="success" variant="success">{{ success }}</Alert>

            <Spinner v-if="loading" size="lg"/>

            <template v-else>
                <ClusterStoragePolicyPanel v-model:reach="reach" v-model:locked="locked"
                                           :saving="saving" :has-backend="hasBackend"
                                           @save="savePolicy" @drop="drop"/>

                <StorageBackendForm
                    v-model:selected-type="selectedType"
                    v-model:s3="s3"
                    v-model:smb="smb"
                    v-model:sftp="sftp"
                    i18n-prefix="clusterStorageBackend"
                    :probing="probing"
                    :saving="saving"
                    show-live-probe
                    :can-probe-live="hasBackend"
                    :probe-outcome="probeOutcome"
                    @probe-config="probeConfig"
                    @probe-live="probeLive"
                    @apply="saveBackend"
                />

                <NeutralContainer class="space-y-3">
                    <SubHeader>{{ t('clusterStorageBackend.placements.title') }}</SubHeader>
                    <MutedText tag="p" size="sm">{{ t('clusterStorageBackend.placements.hint') }}</MutedText>
                    <StoragePlacementTable :placements="placements" :moving-uid="movingUid" @move="move"/>
                </NeutralContainer>
            </template>
        </div>
    </ViewContent>
</template>
