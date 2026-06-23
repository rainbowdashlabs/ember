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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Modal from '@/components/feedback/Modal.vue'
import StorageBackendAuditTable from '@/components/storage/StorageBackendAuditTable.vue'
import S3BackendForm from './stationstoragebackendview/S3BackendForm.vue'
import SmbBackendForm from './stationstoragebackendview/SmbBackendForm.vue'
import SftpBackendForm from './stationstoragebackendview/SftpBackendForm.vue'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {
    type AuditEntry,
    type BackendOverrideResponse,
    type ProbeResult,
    type S3Request,
    type SftpRequest,
    type SmbRequest,
    type StationBackendRequest,
    deleteStationBackend,
    getStationBackend,
    getStationStorageAudit,
    migrateStationBackend,
    probeStationBackend,
    upsertStationBackend,
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
const keepSource = ref(false)

const probing = ref(false)
const saving = ref(false)
const migrating = ref(false)
const probeOutcome = ref<ProbeResult | null>(null)
const confirmMigrate = ref(false)

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
    const req = currentRequest()
    if (!req) {
        probeOutcome.value = null
        return
    }
    probing.value = true
    probeOutcome.value = null
    try {
        probeOutcome.value = await probeStationBackend(req)
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

async function save() {
    error.value = ''
    success.value = ''
    if (selectedType.value === 'LOCAL') {
        await removeOverride()
        return
    }
    const req = currentRequest()
    if (!req) return
    saving.value = true
    try {
        await upsertStationBackend(req)
        success.value = t('stationStorageBackend.feedback.saved')
        await loadAll()
    } catch (e: any) {
        error.value = e?.response?.data?.title ?? e?.message ?? t('stationStorageBackend.errors.saveFailed')
    } finally {
        saving.value = false
    }
}

async function removeOverride() {
    if (!hasOverride.value) {
        success.value = t('stationStorageBackend.feedback.noOverride')
        return
    }
    saving.value = true
    try {
        await deleteStationBackend()
        success.value = t('stationStorageBackend.feedback.deleted')
        await loadAll()
    } catch (e: any) {
        error.value = e?.response?.data?.title ?? e?.message ?? t('stationStorageBackend.errors.deleteFailed')
    } finally {
        saving.value = false
    }
}

async function runMigrate() {
    confirmMigrate.value = false
    const req = currentRequest()
    if (!req) {
        error.value = t('stationStorageBackend.errors.migrateNeedsOverride')
        return
    }
    migrating.value = true
    error.value = ''
    success.value = ''
    try {
        const result = await migrateStationBackend(req)
        success.value = t('stationStorageBackend.feedback.migrated', {
            copied: result.copied,
            skipped: result.skipped,
            deleted: result.deleted,
        })
        await loadAll()
    } catch (e: any) {
        error.value = e?.response?.data?.title ?? e?.message ?? t('stationStorageBackend.errors.migrateFailed')
    } finally {
        migrating.value = false
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
                <SectionHeader>{{ t('stationStorageBackend.title') }}</SectionHeader>
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

                <NeutralContainer class="space-y-4">
                    <SubHeader>{{ t('stationStorageBackend.form.title') }}</SubHeader>
                    <MutedText tag="p" size="sm">{{ t('stationStorageBackend.form.hint') }}</MutedText>

                    <div class="space-y-1">
                        <FieldLabel>{{ t('stationStorageBackend.form.type') }}</FieldLabel>
                        <SelectInput v-model="selectedType">
                            <option value="LOCAL">{{ t('stationStorageBackend.form.types.local') }}</option>
                            <option value="S3">{{ t('stationStorageBackend.form.types.s3') }}</option>
                            <option value="SMB">{{ t('stationStorageBackend.form.types.smb') }}</option>
                            <option value="SFTP">{{ t('stationStorageBackend.form.types.sftp') }}</option>
                        </SelectInput>
                    </div>

                    <MutedText v-if="selectedType === 'LOCAL'" tag="p" size="sm">
                        {{ t('stationStorageBackend.form.localHint') }}
                    </MutedText>
                    <S3BackendForm v-else-if="selectedType === 'S3'" v-model="s3" />
                    <SmbBackendForm v-else-if="selectedType === 'SMB'" v-model="smb" />
                    <SftpBackendForm v-else-if="selectedType === 'SFTP'" v-model="sftp" />

                    <div v-if="probeOutcome" class="text-sm">
                        <Alert v-if="probeOutcome.healthy" variant="success">
                            {{ t('stationStorageBackend.probe.ok') }}
                        </Alert>
                        <Alert v-else variant="error">
                            {{ t('stationStorageBackend.probe.failed', {reason: probeOutcome.error ?? ''}) }}
                        </Alert>
                    </div>

                    <div class="flex flex-wrap items-center gap-3">
                        <SecondaryButton :disabled="probing || selectedType === 'LOCAL'" @click="probe">
                            {{ probing ? t('stationStorageBackend.actions.probing') : t('stationStorageBackend.actions.probe') }}
                        </SecondaryButton>
                        <PrimaryButton :disabled="saving" @click="save">
                            {{ saving ? t('stationStorageBackend.actions.saving') : t('stationStorageBackend.actions.save') }}
                        </PrimaryButton>
                        <DeleteButton v-if="hasOverride" :disabled="saving" @click="removeOverride">
                            {{ t('stationStorageBackend.actions.removeOverride') }}
                        </DeleteButton>
                        <SecondaryButton
                            v-if="hasOverride"
                            :disabled="migrating || selectedType === 'LOCAL'"
                            @click="confirmMigrate = true"
                        >
                            {{ migrating ? t('stationStorageBackend.actions.migrating') : t('stationStorageBackend.actions.migrate') }}
                        </SecondaryButton>
                    </div>
                </NeutralContainer>

                <NeutralContainer class="space-y-3">
                    <SubHeader>{{ t('stationStorageBackend.audit.title') }}</SubHeader>
                    <StorageBackendAuditTable :entries="auditEntries" />
                </NeutralContainer>
            </template>
        </div>

        <Modal v-model="confirmMigrate" size="md">
            <div class="space-y-4">
                <SubHeader>{{ t('stationStorageBackend.confirm.title') }}</SubHeader>
                <MutedText tag="p" size="sm">{{ t('stationStorageBackend.confirm.body') }}</MutedText>
                <FieldLabel inline>
                    <ToggleInput v-model="keepSource" />
                    <span>{{ t('stationStorageBackend.confirm.keepSource') }}</span>
                </FieldLabel>
                <div class="flex justify-end gap-3">
                    <SecondaryButton @click="confirmMigrate = false">
                        {{ t('stationStorageBackend.confirm.cancel') }}
                    </SecondaryButton>
                    <PrimaryButton :disabled="migrating" @click="runMigrate">
                        {{ t('stationStorageBackend.confirm.confirm') }}
                    </PrimaryButton>
                </div>
            </div>
        </Modal>
    </ViewContent>
</template>
