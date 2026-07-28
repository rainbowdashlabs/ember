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
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {
    type InstanceBackendRequest,
    type InstanceBackendSummary,
    type InstanceMigrationStatusResponse,
    type ProbeResult,
    type S3Request,
    type SftpRequest,
    type SmbRequest,
    applyInstanceBackend,
    getInstanceBackend,
    getInstanceMigrationStatus,
    probeInstanceBackend,
    probeInstanceBackendConfig,
} from '@/api/storageBackend'
import {
    newS3,
    newSftp,
    newSmb,
    s3FormFrom,
    sftpFormFrom,
    smbFormFrom,
} from '@/util/storageBackendForm'
import StorageBackendForm from '@/components/storage/StorageBackendForm.vue'
import BackendSummaryCard from './adminstoragebackendview/BackendSummaryCard.vue'
import BackendApplyConfirmModal from './adminstoragebackendview/BackendApplyConfirmModal.vue'

const {t} = useI18n()
const {isAdmin, loaded} = useSession()
const router = useRouter()

watch(loaded, (isLoaded) => {
    if (isLoaded && !isAdmin()) {
        router.replace('/admin')
    }
}, {immediate: true})

const loading = ref(true)
const loadError = ref('')
const success = ref('')
const backend = ref<InstanceBackendSummary | null>(null)
const probeOutcome = ref<ProbeResult | null>(null)
const migrationStatus = ref<InstanceMigrationStatusResponse | null>(null)

const selectedType = ref<'LOCAL' | 'S3' | 'SMB' | 'SFTP'>('LOCAL')
const localRoot = ref('data')
const s3 = ref<S3Request>(newS3())
const smb = ref<SmbRequest>(newSmb())
const sftp = ref<SftpRequest>(newSftp())

const keepSource = ref(false)
const confirmApply = ref(false)

const summaryLabel = computed(() => {
    if (!backend.value) return ''
    return t('adminStorageBackend.summary.current', {type: backend.value.type})
})

onMounted(loadAll)

function backendError(e: unknown, fallback: string): string {
    const err = e as {response?: {data?: {title?: string}}; message?: string}
    return err?.response?.data?.title ?? err?.message ?? fallback
}

async function loadAll() {
    loading.value = true
    loadError.value = ''
    try {
        backend.value = await getInstanceBackend()
        migrationStatus.value = await getInstanceMigrationStatus()
        seedFormFromBackend()
    } catch (e) {
        loadError.value = backendError(e, t('adminStorageBackend.errors.loadFailed'))
    } finally {
        loading.value = false
    }
}

function seedFormFromBackend() {
    const summary = backend.value
    if (!summary) return
    selectedType.value = summary.type
    if (summary.type === 'LOCAL') {
        localRoot.value = summary.root || 'data'
    } else if (summary.type === 'S3') {
        s3.value = s3FormFrom(summary)
    } else if (summary.type === 'SMB') {
        smb.value = smbFormFrom(summary)
    } else if (summary.type === 'SFTP') {
        sftp.value = sftpFormFrom(summary)
    }
}

function currentRequest(): InstanceBackendRequest {
    if (selectedType.value === 'LOCAL') return {type: 'LOCAL', root: localRoot.value || 'data'}
    if (selectedType.value === 'S3') return s3.value
    if (selectedType.value === 'SMB') return smb.value
    return sftp.value
}

function failedProbe(e: unknown): ProbeResult {
    return {
        healthy: false,
        error: backendError(e, t('adminStorageBackend.errors.probeFailed')),
        checkedAt: new Date().toISOString(),
    }
}

const {running: probingLive, run: probeLive} = useAsyncAction(async () => {
    probeOutcome.value = null
    try {
        probeOutcome.value = await probeInstanceBackend()
    } catch (e) {
        probeOutcome.value = failedProbe(e)
    }
})

const {running: probingConfig, run: probeConfig} = useAsyncAction(async () => {
    probeOutcome.value = null
    try {
        probeOutcome.value = await probeInstanceBackendConfig(currentRequest())
    } catch (e) {
        probeOutcome.value = failedProbe(e)
    }
})

const {running: saving, error: applyError, run: runApply} = useAsyncAction(async () => {
    confirmApply.value = false
    success.value = ''
    const result = await applyInstanceBackend({target: currentRequest(), keepSource: keepSource.value})
    success.value = t('adminStorageBackend.feedback.applied', {
        copied: result.copied,
        skipped: result.skipped,
        deleted: result.deleted,
    })
    await loadAll()
}, {formatError: (e) => backendError(e, t('adminStorageBackend.errors.applyFailed'))})

const error = computed(() => loadError.value || applyError.value)

</script>

<template>
    <ViewContent :title="t('pages.admin-storage-backend.title')" :subtitle="t('pages.admin-storage-backend.subtitle')">
        <div class="space-y-6">
            <div class="flex items-center justify-between">
                <div class="flex gap-3 text-sm">
                    <RouterLink :to="{name: 'admin-storage'}" class="underline">
                        {{ t('adminStorageBackend.linkUsage') }}
                    </RouterLink>
                    <RouterLink :to="{name: 'admin-storage-audit'}" class="underline">
                        {{ t('adminStorageBackend.linkAudit') }}
                    </RouterLink>
                </div>
            </div>

            <Alert v-if="migrationStatus?.migrationInFlight" variant="info">
                {{ t('adminStorageBackend.banner.inFlight') }}
            </Alert>
            <Alert v-if="error" variant="error">{{ error }}</Alert>
            <Alert v-if="success" variant="success">{{ success }}</Alert>

            <Spinner v-if="loading" size="lg"/>

            <template v-else-if="backend">
                <BackendSummaryCard
                    :summary-label="summaryLabel"
                    :probing-live="probingLive"
                    :probe-outcome="probeOutcome"
                    @probe="probeLive"/>
                <StorageBackendForm
                    v-model:selected-type="selectedType"
                    v-model:local-root="localRoot"
                    v-model:s3="s3"
                    v-model:smb="smb"
                    v-model:sftp="sftp"
                    i18n-prefix="adminStorageBackend"
                    :probing="probingConfig"
                    :saving="saving"
                    @probe-config="probeConfig"
                    @apply="confirmApply = true"/>
            </template>
        </div>

        <BackendApplyConfirmModal
            v-model="confirmApply"
            v-model:keep-source="keepSource"
            :saving="saving"
            @confirm="runApply"/>
    </ViewContent>
</template>
