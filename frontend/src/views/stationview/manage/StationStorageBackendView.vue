/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, RouterLink} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import StorageApplyConfirmModal from '@/components/storage/StorageApplyConfirmModal.vue'
import StorageBackendForm from '@/components/storage/StorageBackendForm.vue'
import StorageBackendHistory from '@/components/storage/StorageBackendHistory.vue'
import StorageBackendSummaryCard from '@/components/storage/StorageBackendSummaryCard.vue'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {type StorageBackendChoice, useStorageBackendEditor} from '@/composables/useStorageBackendEditor'
import {
    applyStationBackend,
    getStationBackend,
    getStationStorageAudit,
    probeStationBackend,
    probeStationBackendConfig,
} from '@/api/storageBackend'
import type {AuditEntryResponse, BackendOverrideResponse} from '@/api/generated/schema'

const {t} = useI18n()
const {hasPermission, loaded} = useSession()
const router = useRouter()

watch(loaded, (isLoaded) => {
    if (isLoaded && !hasPermission(StationPermission.STATION_ADMINISTRATOR)) {
        router.replace('/station/dashboard/overview')
    }
}, {immediate: true})

const backend = ref<BackendOverrideResponse | null>(null)
const auditEntries = ref<AuditEntryResponse[]>([])

const editor = useStorageBackendEditor({
    probeSaved: probeStationBackend,
    probeTyped: (request) => probeStationBackendConfig(request),
    reload: () => loadAll(),
    reloadHistory: async () => { auditEntries.value = await getStationStorageAudit() },
}, 'LOCAL')

const {loading, failure: loadFailure, reload: loadAll} = useAsyncLoader(async () => {
    backend.value = await getStationBackend()
    editor.seed(backend.value.override, 'LOCAL')
    auditEntries.value = await getStationStorageAudit()
})
const {selectedType, s3, smb, sftp, savedOutcome, typedOutcome, success, pending, probing, saving, failure} = editor

const overrideType = computed(() => backend.value?.override?.type ?? null)
const onClusterStorage = computed(() => backend.value?.clusterBackend != null)
const locked = computed(() => backend.value?.locked === true)

/**
 * Where this station's files are, and what it falls back to.
 *
 * Three answers rather than two, because a station under an association may be standing on the
 * association's storage, and it will not have been the station that put it there.
 */
const summaryLines = computed(() => {
    if (!backend.value) return []
    const defaultLine = t('stationStorageBackend.summary.instanceDefault', {type: backend.value.instanceDefault})
    if (overrideType.value) return [t('stationStorageBackend.summary.override', {type: overrideType.value}), defaultLine]
    if (onClusterStorage.value) {
        return [
            t('stationStorageBackend.summary.cluster', {
                type: backend.value.clusterBackend!.type,
                cluster: backend.value.clusterName ?? '',
            }),
            defaultLine,
        ]
    }
    return [t('stationStorageBackend.summary.inherit', {type: backend.value.instanceDefault}), defaultLine]
})

/**
 * What this station may point itself at. Its association's storage only when there is some to move onto,
 * and nothing at all when the association has said it decides.
 */
const offeredTypes = computed<StorageBackendChoice[]>(() =>
    backend.value?.clusterOffersStorage
        ? ['LOCAL', 'CLUSTER', 'S3', 'SMB', 'SFTP']
        : ['LOCAL', 'S3', 'SMB', 'SFTP'],
)

function requestApply() {
    const request = editor.currentRequest()
    editor.askFirst(t('stationStorageBackend.confirm.body'), async () => {
        const result = await applyStationBackend(request)
        return t('stationStorageBackend.feedback.applied', {
            copied: result.copied,
            skipped: result.skipped,
            deleted: result.deleted,
        })
    })
}
</script>

<template>
    <ViewContent
        :title="t('pages.station-storage-backend.title')"
        :subtitle="t('pages.station-storage-backend.subtitle')"
    >
        <div class="space-y-6">
            <div class="flex items-center justify-end">
                <RouterLink :to="{name: 'station-storage'}" class="text-sm underline">
                    {{ t('stationStorageBackend.backToUsage') }}
                </RouterLink>
            </div>

            <FailureAlert :failure="loadFailure"/>
            <FailureAlert :failure="failure"/>
            <Alert v-if="success" variant="success">{{ success }}</Alert>

            <Spinner v-if="loading" size="lg" />

            <template v-else-if="backend">
                <StorageBackendSummaryCard
                    :lines="summaryLines"
                    :can-probe="overrideType !== null"
                    :probing="probing"
                    :probe-outcome="savedOutcome"
                    @probe="editor.probeSaved"
                />

                <Alert v-if="locked" variant="info" data-testid="station-storage-locked">
                    {{ t('stationStorageBackend.lockedByCluster', {cluster: backend.clusterName ?? ''}) }}
                </Alert>

                <StorageBackendForm
                    v-if="!locked"
                    v-model:selected-type="selectedType"
                    v-model:s3="s3"
                    v-model:smb="smb"
                    v-model:sftp="sftp"
                    i18n-prefix="stationStorageBackend"
                    :types="offeredTypes"
                    :probing="probing"
                    :saving="saving"
                    :probe-outcome="typedOutcome"
                    @probe-config="editor.probeTyped"
                    @apply="requestApply"
                />

                <StorageBackendHistory :entries="auditEntries"/>
            </template>
        </div>

        <StorageApplyConfirmModal
            :title="t('stationStorageBackend.confirm.title')"
            :body="pending?.body ?? null"
            :saving="saving"
            @confirm="editor.confirmPending"
            @cancel="editor.cancelPending"
        />
    </ViewContent>
</template>
