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
    } catch (e) {
        error.value = apiErrorTitle(e, t('stationStorageBackend.errors.loadFailed'))
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

function apiErrorTitle(e: unknown, fallback: string): string {
    const err = e as {response?: {data?: {title?: string}}; message?: string}
    return err?.response?.data?.title ?? err?.message ?? fallback
}

function currentRequest(): StationBackendRequest | null {
    if (selectedType.value === 'LOCAL') return null
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
            error: apiErrorTitle(e, t('stationStorageBackend.errors.probeFailed')),
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
    return currentRequest()!
}

const {running: saving, error: applyError, run: runApply} = useAsyncAction(
    async () => {
        confirmApply.value = false
        error.value = ''
        success.value = ''
        const result = await applyStationBackend(applyRequest())
        success.value = t('stationStorageBackend.feedback.applied', {
            copied: result.copied,
            skipped: result.skipped,
            deleted: result.deleted,
        })
        await loadAll()
    },
    {formatError: (e) => apiErrorTitle(e, t('stationStorageBackend.errors.applyFailed'))},
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

            <Alert v-if="error || applyError" variant="error">{{ error || applyError }}</Alert>
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

                <StorageBackendForm
                    v-model:selected-type="selectedType"
                    v-model:s3="s3"
                    v-model:smb="smb"
                    v-model:sftp="sftp"
                    i18n-prefix="stationStorageBackend"
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
