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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {useSession} from '@/composables/useSession'
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
import BackendSummaryCard from './adminstoragebackendview/BackendSummaryCard.vue'
import BackendForm from './adminstoragebackendview/BackendForm.vue'
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
const error = ref('')
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
const probingLive = ref(false)
const probingConfig = ref(false)
const saving = ref(false)
const confirmApply = ref(false)

const summaryLabel = computed(() => {
    if (!backend.value) return ''
    return t('adminStorageBackend.summary.current', {type: backend.value.type})
})

onMounted(loadAll)

async function loadAll() {
    loading.value = true
    error.value = ''
    try {
        backend.value = await getInstanceBackend()
        migrationStatus.value = await getInstanceMigrationStatus()
        seedFormFromBackend()
    } catch (e: any) {
        error.value = e?.response?.data?.title ?? e?.message ?? t('adminStorageBackend.errors.loadFailed')
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
        smb.value = {
            type: 'SMB',
            host: summary.host,
            port: summary.port,
            share: summary.share,
            domain: '',
            basePath: summary.basePath,
            seal: summary.seal,
            dfs: summary.dfs,
            username: '',
            password: '',
        }
    } else if (summary.type === 'SFTP') {
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

function currentRequest(): InstanceBackendRequest {
    if (selectedType.value === 'LOCAL') return {type: 'LOCAL', root: localRoot.value || 'data'}
    if (selectedType.value === 'S3') return s3.value
    if (selectedType.value === 'SMB') return smb.value
    return sftp.value
}

async function probeLive() {
    probingLive.value = true
    probeOutcome.value = null
    try {
        probeOutcome.value = await probeInstanceBackend()
    } catch (e: any) {
        probeOutcome.value = {
            healthy: false,
            error: e?.response?.data?.title ?? e?.message ?? t('adminStorageBackend.errors.probeFailed'),
            checkedAt: new Date().toISOString(),
        }
    } finally {
        probingLive.value = false
    }
}

async function probeConfig() {
    probingConfig.value = true
    probeOutcome.value = null
    try {
        probeOutcome.value = await probeInstanceBackendConfig(currentRequest())
    } catch (e: any) {
        probeOutcome.value = {
            healthy: false,
            error: e?.response?.data?.title ?? e?.message ?? t('adminStorageBackend.errors.probeFailed'),
            checkedAt: new Date().toISOString(),
        }
    } finally {
        probingConfig.value = false
    }
}

async function runApply() {
    confirmApply.value = false
    saving.value = true
    error.value = ''
    success.value = ''
    try {
        const result = await applyInstanceBackend({target: currentRequest(), keepSource: keepSource.value})
        success.value = t('adminStorageBackend.feedback.applied', {
            copied: result.copied,
            skipped: result.skipped,
            deleted: result.deleted,
        })
        await loadAll()
    } catch (e: any) {
        error.value = e?.response?.data?.title ?? e?.message ?? t('adminStorageBackend.errors.applyFailed')
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
    <ViewContent>
        <div class="space-y-6">
            <div class="flex items-center justify-between">
                <SectionHeader>{{ t('adminStorageBackend.title') }}</SectionHeader>
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
                <BackendForm
                    v-model:selected-type="selectedType"
                    v-model:local-root="localRoot"
                    v-model:s3="s3"
                    v-model:smb="smb"
                    v-model:sftp="sftp"
                    :probing-config="probingConfig"
                    :saving="saving"
                    @probe="probeConfig"
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
