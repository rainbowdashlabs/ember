/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
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

/**
 * How the list is ordered. Sorting by the partner is the one the page exists for: with several
 * partners the origin lives on the row rather than in the heading, so it has to be possible to bring
 * one partner's gear together.
 */
type SortKey = 'owner' | 'name' | 'due'
const sortKey = ref<SortKey>('owner')

const sorted = computed<BorrowedItem[]>(() => {
    const copy = [...rows.value]
    if (sortKey.value === 'name') {
        return copy.sort((a, b) => (a.item.name ?? '').localeCompare(b.item.name ?? ''))
    }
    if (sortKey.value === 'due') {
        return copy.sort((a, b) => (a.dueOn ?? '').localeCompare(b.dueOn ?? ''))
    }
    return copy.sort(
        (a, b) =>
            a.ownerStationName.localeCompare(b.ownerStationName) ||
            (a.item.name ?? '').localeCompare(b.item.name ?? ''),
    )
})

const {loading, error, reload} = useAsyncLoader(
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
            :empty="sorted.length === 0"
            :empty-message="t('inventory.borrowed.empty')"
            :error="error"
            :loading="loading"
        >
            <BorrowedGearTable v-model:sort-key="sortKey" :rows="sorted" />
        </AsyncSection>
    </ViewContent>
</template>
