/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, RouterLink} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import StorageBackendAuditTable from '@/components/storage/StorageBackendAuditTable.vue'
import {useSession} from '@/composables/useSession'
import {type AuditEntry, getInstanceStorageAudit} from '@/api/storageBackend'
import {errorMessage} from '@/util/apiError'

const {t} = useI18n()
const {isAdmin, loaded} = useSession()
const router = useRouter()

watch(loaded, (isLoaded) => {
    if (isLoaded && !isAdmin()) {
        router.replace('/admin')
    }
}, {immediate: true})

const loading = ref(false)
const error = ref('')
const entries = ref<AuditEntry[]>([])
const stationUidFilter = ref('')
const beforeFilter = ref('')
const limit = ref(50)

onMounted(applyFilters)

async function applyFilters() {
    loading.value = true
    error.value = ''
    try {
        entries.value = await getInstanceStorageAudit({
            stationUid: stationUidFilter.value || undefined,
            before: beforeFilter.value || undefined,
            limit: limit.value,
        })
    } catch (e) {
        error.value = errorMessage(e) ?? t('adminStorageAudit.errors.loadFailed')
    } finally {
        loading.value = false
    }
}
</script>

<template>
    <ViewContent :title="t('pages.admin-storage-audit.title')" :subtitle="t('pages.admin-storage-audit.subtitle')">
        <div class="space-y-6">
            <div class="flex items-center justify-between">
                <RouterLink :to="{name: 'admin-storage-backend'}" class="text-sm underline">
                    {{ t('adminStorageAudit.linkBackend') }}
                </RouterLink>
            </div>

            <Alert v-if="error" variant="error">{{ error }}</Alert>

            <NeutralContainer class="space-y-4">
                <SubHeader>{{ t('adminStorageAudit.filters.title') }}</SubHeader>
                <MutedText tag="p" size="sm">{{ t('adminStorageAudit.filters.hint') }}</MutedText>
                <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
                    <div class="space-y-1">
                        <FieldLabel>{{ t('adminStorageAudit.filters.stationUid') }}</FieldLabel>
                        <TextInput v-model="stationUidFilter" placeholder="00000000-0000-0000-0000-000000000000" />
                    </div>
                    <div class="space-y-1">
                        <FieldLabel>{{ t('adminStorageAudit.filters.before') }}</FieldLabel>
                        <TextInput v-model="beforeFilter" placeholder="2026-01-01T00:00:00Z" />
                    </div>
                    <div class="space-y-1">
                        <FieldLabel>{{ t('adminStorageAudit.filters.limit') }}</FieldLabel>
                        <NumberInput v-model="limit" />
                    </div>
                </div>
                <div>
                    <PrimaryButton :disabled="loading" @click="applyFilters">
                        {{ loading ? t('adminStorageAudit.actions.loading') : t('adminStorageAudit.actions.apply') }}
                    </PrimaryButton>
                </div>
            </NeutralContainer>

            <Spinner v-if="loading" size="lg" />

            <NeutralContainer v-else class="space-y-3">
                <SubHeader>{{ t('adminStorageAudit.results.title') }}</SubHeader>
                <StorageBackendAuditTable :entries="entries" />
            </NeutralContainer>
        </div>
    </ViewContent>
</template>
