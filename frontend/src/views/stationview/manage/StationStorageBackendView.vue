/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, RouterLink} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import StorageBackendSummary from './stationstoragebackendview/StorageBackendSummary.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Modal from '@/components/feedback/Modal.vue'
import StorageBackendAuditTable from '@/components/storage/StorageBackendAuditTable.vue'
import StorageBackendForm from '@/components/storage/StorageBackendForm.vue'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {
    type AuditEntry,
    type BackendOverrideResponse,
    type ProbeResult,
    type S3Request,
    type SftpRequest,
    type SmbRequest,
    type StationApplyRequest,
    type StationBackendRequest,
    applyStationBackend,
    getStationBackend,
    getStationStorageAudit,
    probeStationBackend,
    probeStationBackendConfig,
} from '@/api/storageBackend'
import {
    newS3,
    newSftp,
    newSmb,
    s3FormFrom,
    sftpFormFrom,
    smbFormFrom,
} from '@/util/storageBackendForm'
import {describeFailure, type Failure} from '@/util/failure'

const {t} = useI18n()
const {hasPermission, loaded} = useSession()
const router = useRouter()

watch(loaded, (isLoaded) => {
    if (isLoaded && !hasPermission(StationPermission.STATION_ADMINISTRATOR)) {
        router.replace('/station/dashboard/overview')
    }
}, {immediate: true})

const loading = ref(true)
const loadFailure = ref<Failure | null>(null)
const success = ref('')
const backend = ref<BackendOverrideResponse | null>(null)
const auditEntries = ref<AuditEntry[]>([])

const selectedType = ref<'LOCAL' | 'CLUSTER' | 'S3' | 'SMB' | 'SFTP'>('LOCAL')
const s3 = ref<S3Request>(newS3())
const smb = ref<SmbRequest>(newSmb())
const sftp = ref<SftpRequest>(newSftp())
const probeOutcome = ref<ProbeResult | null>(null)
const confirmApply = ref(false)

const overrideType = computed(() => backend.value?.override?.type ?? null)
const hasOverride = computed(() => overrideType.value !== null)
const onClusterStorage = computed(() => backend.value?.clusterBackend != null)
const locked = computed(() => backend.value?.locked === true)

/**
 * Where this station's files are, in one line.
 *
 * Three answers rather than two, because a station under an association may be standing on the
 * association's storage, and it will not have been the station that put it there.
 */
const activeBackendLabel = computed(() => {
    if (!backend.value) return ''
    if (overrideType.value) return t('stationStorageBackend.summary.override', {type: overrideType.value})
    if (onClusterStorage.value) {
        return t('stationStorageBackend.summary.cluster', {
            type: backend.value.clusterBackend!.type,
            cluster: backend.value.clusterName ?? '',
        })
    }
    return t('stationStorageBackend.summary.inherit', {type: backend.value.instanceDefault})
})

/**
 * What this station may point itself at. Its association's storage only when there is some to move onto,
 * and nothing at all when the association has said it decides.
 */
const offeredTypes = computed<('LOCAL' | 'CLUSTER' | 'S3' | 'SMB' | 'SFTP')[]>(() =>
    backend.value?.clusterOffersStorage
        ? ['LOCAL', 'CLUSTER', 'S3', 'SMB', 'SFTP']
        : ['LOCAL', 'S3', 'SMB', 'SFTP'],
)

onMounted(loadAll)

async function loadAll() {
    loading.value = true
    loadFailure.value = null
    try {
        backend.value = await getStationBackend()
        seedFormFromBackend()
        auditEntries.value = await getStationStorageAudit()
    } catch (e) {
        loadFailure.value = describeFailure(e, t)
    } finally {
        loading.value = false
    }
}

function seedFormFromBackend() {
    const summary = backend.value?.override
    if (!summary) {
        selectedType.value = 'LOCAL'
        return
    }
    selectedType.value = summary.type
    if (summary.type === 'S3') {
        s3.value = s3FormFrom(summary)
    } else if (summary.type === 'SMB') {
        smb.value = smbFormFrom(summary)
    } else if (summary.type === 'SFTP') {
        sftp.value = sftpFormFrom(summary)
    }
}

function currentRequest(): StationBackendRequest | null {
    if (selectedType.value === 'LOCAL' || selectedType.value === 'CLUSTER') return null
    if (selectedType.value === 'S3') return s3.value
    if (selectedType.value === 'SMB') return smb.value
    return sftp.value
}

const {running: probing, run: runProbe} = useAsyncAction(async (call: () => Promise<ProbeResult>) => {
    try {
        probeOutcome.value = await call()
    } catch (e) {
        probeOutcome.value = {
            healthy: false,
            error: describeFailure(e, t).message,
            checkedAt: new Date().toISOString(),
        }
    }
})

function probe() {
    if (!hasOverride.value) {
        probeOutcome.value = null
        return
    }
    probeOutcome.value = null
    return runProbe(() => probeStationBackend())
}

function probeConfig() {
    const req = currentRequest()
    if (!req) {
        probeOutcome.value = null
        return
    }
    probeOutcome.value = null
    return runProbe(() => probeStationBackendConfig(req))
}

function applyRequest(): StationApplyRequest {
    if (selectedType.value === 'LOCAL') return {type: 'LOCAL'}
    if (selectedType.value === 'CLUSTER') return {type: 'CLUSTER'}
    return currentRequest()!
}

const {running: saving, failure: applyFailure, run: runApply} = useAsyncAction(
    async () => {
        confirmApply.value = false
        success.value = ''
        const result = await applyStationBackend(applyRequest())
        success.value = t('stationStorageBackend.feedback.applied', {
            copied: result.copied,
            skipped: result.skipped,
            deleted: result.deleted,
        })
        await loadAll()
    },
    {formatError: (e) => describeFailure(e, t).message},
)

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
            <FailureAlert :failure="applyFailure"/>
            <Alert v-if="success" variant="success">{{ success }}</Alert>

            <Spinner v-if="loading" size="lg" />

            <template v-else-if="backend">
                <StorageBackendSummary
                    :where="activeBackendLabel"
                    :instance-default="backend.instanceDefault"
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
                    show-live-probe
                    :can-probe-live="hasOverride"
                    :probe-outcome="probeOutcome"
                    @probe-config="probeConfig"
                    @probe-live="probe"
                    @apply="confirmApply = true"
                />

                <NeutralContainer class="space-y-3">
                    <SubHeader>{{ t('stationStorageBackend.audit.title') }}</SubHeader>
                    <StorageBackendAuditTable :entries="auditEntries" />
                </NeutralContainer>
            </template>
        </div>

        <Modal v-model="confirmApply" size="md">
            <div class="space-y-4">
                <SubHeader>{{ t('stationStorageBackend.confirm.title') }}</SubHeader>
                <MutedText tag="p" size="sm">{{ t('stationStorageBackend.confirm.body') }}</MutedText>
                <ButtonRow pair align="end">
                    <SecondaryButton @click="confirmApply = false">
                        {{ t('stationStorageBackend.confirm.cancel') }}
                    </SecondaryButton>
                    <PrimaryButton :disabled="saving" @click="runApply">
                        {{ t('stationStorageBackend.confirm.confirm') }}
                    </PrimaryButton>
                </ButtonRow>
            </div>
        </Modal>
    </ViewContent>
</template>
