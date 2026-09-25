/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import Alert from '@/components/feedback/Alert.vue'
import BorrowedGearTable from './borrowedgearview/BorrowedGearTable.vue'
import type {BorrowedItem} from '@/api/inventory'
import {inventory} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {t} = useI18n()
const {loaded} = useSession()

const rows = ref<BorrowedItem[]>([])

const {loading, failure, reload} = useAsyncLoader(
    async () => {
        if (!loaded.value) return
        rows.value = await inventory.listBorrowed()
    },
    {errorMessageKey: 'inventory.borrowed.loadError'},
)

watch(loaded, v => {
    if (v) reload()
})
</script>

<template>
    <ViewContent :title="t('pages.inventory-borrowed.title')" :subtitle="t('pages.inventory-borrowed.subtitle')">
        <Alert class="mb-4" variant="info">{{ t('inventory.borrowed.snapshotNote') }}</Alert>

        <AsyncSection
            :empty="rows.length === 0"
            :empty-message="t('inventory.borrowed.empty')"
            :failure="failure"
            :loading="loading"
        >
            <BorrowedGearTable :rows="rows" />
        </AsyncSection>
    </ViewContent>
</template>
