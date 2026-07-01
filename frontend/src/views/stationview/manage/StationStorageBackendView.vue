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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Modal from '@/components/feedback/Modal.vue'
import StorageBackendAuditTable from '@/components/storage/StorageBackendAuditTable.vue'
import BackendForm from './stationstoragebackendview/BackendForm.vue'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
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

const {t} = useI18n()
const {hasPermission, loaded} = useSession()
const router = useRouter()

watch(loaded, (isLoaded) => {
    if (isLoaded && !hasPermission(StationPermission.STATION_ADMINISTRATOR)) {
        router.replace('/station/dashboard/overview')
    }
}, {immediate: true})

const loading = ref(true)
const error = ref('')
const success = ref('')
const backend = ref<BackendOverrideResponse | null>(null)
const auditEntries = ref<AuditEntry[]>([])

const selectedType = ref<'LOCAL' | 'S3' | 'SMB' | 'SFTP'>('LOCAL')
const s3 = ref<S3Request>(newS3())
const smb = ref<SmbRequest>(newSmb())
const sftp = ref<SftpRequest>(newSftp())
const probing = ref(false)
const saving = ref(false)
const probeOutcome = ref<ProbeResult | null>(null)
const confirmApply = ref(false)

const overrideType = computed(() => backend.value?.override?.type ?? null)
const hasOverride = computed(() => overrideType.value !== null)

const activeBackendLabel = computed(() => {
    if (!backend.value) return ''
    return overrideType.value
        ? t('stationStorageBackend.summary.override', {type: overrideType.value})
        : t('stationStorageBackend.summary.inherit', {type: backend.value.instanceDefault})
})

onMounted(loadAll)

async function loadAll() {
    loading.value = true
    error.value = ''
    try {
        backend.value = await getStationBackend()
        seedFormFromBackend()
        auditEntries.value = await getStationStorageAudit()
    } catch (e: any) {
        error.value = e?.response?.data?.title ?? e?.message ?? t('stationStorageBackend.errors.loadFailed')
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
    if (summary.type === 'S3') {
        selectedType.value = 'S3'
        s3.value = {
            type: 'S3',
            endpoint: summary.endpoint,
            region: summary.region,
            bucket: summary.bucket,
            pathStyle: summary.pathStyle,
            sseAlgorithm: summary.sseAlgorithm ?? '',
            basePath: summary.basePath,
            accessKey: '',
            secretKey: '',
        }
    } else if (summary.type === 'SMB') {
        selectedType.value = 'SMB'
        smb.value = {
            type: 'SMB',
            host: summary.host,
            port: summary.port,
            share: summary.share,
            domain: summary.domain ?? '',
            basePath: summary.basePath,
            seal: summary.seal,
            dfs: summary.dfs,
            username: '',
            password: '',
        }
    } else if (summary.type === 'SFTP') {
        selectedType.value = 'SFTP'
        sftp.value = {
            type: 'SFTP',
            host: summary.host,
            port: summary.port,
            username: summary.username,
            knownHostsFingerprint: '',
            basePath: summary.basePath,
            password: '',
            privateKey: '',
        }
    }
}

function currentRequest(): StationBackendRequest | null {
    if (selectedType.value === 'LOCAL') return null
    if (selectedType.value === 'S3') return s3.value
    if (selectedType.value === 'SMB') return smb.value
    return sftp.value
}

async function probe() {
    if (!hasOverride.value) {
        probeOutcome.value = null
        return
    }
    probing.value = true
    probeOutcome.value = null
    try {
        probeOutcome.value = await probeStationBackend()
    } catch (e: any) {
        probeOutcome.value = {
            healthy: false,
            error: e?.response?.data?.title ?? e?.message ?? t('stationStorageBackend.errors.probeFailed'),
            checkedAt: new Date().toISOString(),
        }
    } finally {
        probing.value = false
    }
}

async function probeConfig() {
    const req = currentRequest()
    if (!req) {
        probeOutcome.value = null
        return
    }
    probing.value = true
    probeOutcome.value = null
    try {
        probeOutcome.value = await probeStationBackendConfig(req)
    } catch (e: any) {
        probeOutcome.value = {
            healthy: false,
            error: e?.response?.data?.title ?? e?.message ?? t('stationStorageBackend.errors.probeFailed'),
            checkedAt: new Date().toISOString(),
        }
    } finally {
        probing.value = false
    }
}

function applyRequest(): StationApplyRequest {
    if (selectedType.value === 'LOCAL') return {type: 'LOCAL'}
    return currentRequest()!
}

async function runApply() {
    confirmApply.value = false
    saving.value = true
    error.value = ''
    success.value = ''
    try {
        const result = await applyStationBackend(applyRequest())
        success.value = t('stationStorageBackend.feedback.applied', {
            copied: result.copied,
            skipped: result.skipped,
            deleted: result.deleted,
        })
        await loadAll()
    } catch (e: any) {
        error.value = e?.response?.data?.title ?? e?.message ?? t('stationStorageBackend.errors.applyFailed')
    } finally {
        saving.value = false
    }
}

function newS3(): S3Request {
    return {
        type: 'S3',
        endpoint: '',
        region: '',
        bucket: '',
        pathStyle: false,
        sseAlgorithm: '',
        basePath: '',
        accessKey: '',
        secretKey: '',
    }
}

function newSmb(): SmbRequest {
    return {
        type: 'SMB',
        host: '',
        port: 445,
        share: '',
        domain: '',
        basePath: '',
        seal: true,
        dfs: false,
        username: '',
        password: '',
    }
}

function newSftp(): SftpRequest {
    return {
        type: 'SFTP',
        host: '',
        port: 22,
        username: '',
        knownHostsFingerprint: '',
        basePath: '',
        password: '',
        privateKey: '',
    }
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

            <Alert v-if="error" variant="error">{{ error }}</Alert>
            <Alert v-if="success" variant="success">{{ success }}</Alert>

            <Spinner v-if="loading" size="lg" />

            <template v-else-if="backend">
                <NeutralContainer class="space-y-2">
                    <SubHeader>{{ t('stationStorageBackend.summary.title') }}</SubHeader>
                    <MutedText tag="p" size="sm">{{ activeBackendLabel }}</MutedText>
                    <MutedText tag="p" size="sm">
                        {{ t('stationStorageBackend.summary.instanceDefault', {type: backend.instanceDefault}) }}
                    </MutedText>
                </NeutralContainer>

                <BackendForm
                    v-model:selected-type="selectedType"
                    v-model:s3="s3"
                    v-model:smb="smb"
                    v-model:sftp="sftp"
                    :probing="probing"
                    :saving="saving"
                    :has-override="hasOverride"
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
                <div class="flex justify-end gap-3">
                    <SecondaryButton @click="confirmApply = false">
                        {{ t('stationStorageBackend.confirm.cancel') }}
                    </SecondaryButton>
                    <PrimaryButton :disabled="saving" @click="runApply">
                        {{ t('stationStorageBackend.confirm.confirm') }}
                    </PrimaryButton>
                </div>
            </div>
        </Modal>
    </ViewContent>
</template>
