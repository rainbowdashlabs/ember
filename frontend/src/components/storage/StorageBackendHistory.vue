/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import StorageBackendAuditTable from '@/components/storage/StorageBackendAuditTable.vue'
import type {AuditEntryResponse} from '@/api/generated/schema'

/** The latest changes to an owner's storage, at the foot of its storage screen. */
defineProps<{
    entries: AuditEntryResponse[]
}>()

const {t} = useI18n()
</script>

<template>
    <NeutralContainer class="space-y-3">
        <SubHeader>{{ t('storageBackend.audit.title') }}</SubHeader>
        <StorageBackendAuditTable :entries="entries"/>
    </NeutralContainer>
</template>
