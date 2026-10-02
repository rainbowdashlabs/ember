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
import StorageBackendSummaryCard from '@/components/storage/StorageBackendSummaryCard.vue'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useStorageBackendEditor} from '@/composables/useStorageBackendEditor'
import {
    applyInstanceBackend,
    getInstanceBackend,
    getInstanceMigrationStatus,
    probeInstanceBackend,
    probeInstanceBackendConfig,
} from '@/api/storageBackend'
import type {BackendSummary, InstanceMigrationStatusResponse} from '@/api/generated/schema'

const {t} = useI18n()
const {isAdmin, loaded} = useSession()
const router = useRouter()

watch(loaded, (isLoaded) => {
    if (isLoaded && !isAdmin()) {
        router.replace('/admin')
    }
}, {immediate: true})

const backend = ref<BackendSummary | null>(null)
const migrationStatus = ref<InstanceMigrationStatusResponse | null>(null)
const keepSource = ref(false)

const editor = useStorageBackendEditor({
    probeSaved: probeInstanceBackend,
    probeTyped: (request) => probeInstanceBackendConfig(request),
    reload: () => loadAll(),
}, 'LOCAL')
const {selectedType, localRoot, s3, smb, sftp, savedOutcome, typedOutcome, success, pending, probing, saving, failure}
    = editor

const {loading, failure: loadFailure, reload: loadAll} = useAsyncLoader(async () => {
    backend.value = await getInstanceBackend()
    migrationStatus.value = await getInstanceMigrationStatus()
    editor.seed(backend.value, 'LOCAL')
})

const summaryLines = computed(() =>
    backend.value ? [t('adminStorageBackend.summary.current', {type: backend.value.type})] : [],
)

function requestApply() {
    const target = editor.currentRequest()
    keepSource.value = false
    editor.askFirst(t('adminStorageBackend.confirm.body'), async () => {
        const result = await applyInstanceBackend({target, keepSource: keepSource.value})
        return t('adminStorageBackend.feedback.applied', {
            copied: result.copied,
            skipped: result.skipped,
            deleted: result.deleted,
        })
    })
}
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
            <FailureAlert :failure="loadFailure"/>
            <FailureAlert :failure="failure"/>
            <Alert v-if="success" variant="success">{{ success }}</Alert>

            <Spinner v-if="loading" size="lg"/>

            <template v-else-if="backend">
                <StorageBackendSummaryCard
                    :lines="summaryLines"
                    can-probe
                    :probing="probing"
                    :probe-outcome="savedOutcome"
                    @probe="editor.probeSaved"/>
                <StorageBackendForm
                    v-model:selected-type="selectedType"
                    v-model:local-root="localRoot"
                    v-model:s3="s3"
                    v-model:smb="smb"
                    v-model:sftp="sftp"
                    i18n-prefix="adminStorageBackend"
                    :probing="probing"
                    :saving="saving"
                    :probe-outcome="typedOutcome"
                    @probe-config="editor.probeTyped"
                    @apply="requestApply"/>
            </template>
        </div>

        <StorageApplyConfirmModal
            v-model:keep-source="keepSource"
            :title="t('adminStorageBackend.confirm.title')"
            :body="pending?.body ?? null"
            :saving="saving"
            @confirm="editor.confirmPending"
            @cancel="editor.cancelPending"/>
    </ViewContent>
</template>
