/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Td from '@/components/table/Td.vue'
import Th from '@/components/table/Th.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import type {BorrowedItem} from '@/api/inventory'
import {formatDate} from '@/util/format'

type SortKey = 'owner' | 'name' | 'due'

defineProps<{rows: BorrowedItem[]}>()

const sortKey = defineModel<SortKey>('sortKey', {required: true})

const {t} = useI18n()
</script>

<template>
    <NeutralContainer>
        <div class="mb-3 flex flex-wrap items-center gap-2">
            <SecondaryButton
                v-for="key in (['owner', 'name', 'due'] as SortKey[])"
                :key="key"
                :class="sortKey === key ? 'ring-2 ring-primary' : ''"
                @click="sortKey = key"
            >
                {{ t(`inventory.borrowed.sortBy.${key}`) }}
            </SecondaryButton>
        </div>

        <div class="overflow-x-auto">
            <table class="w-full text-sm">
                <thead>
                    <THead>
                        <Th>{{ t('inventory.borrowed.colName') }}</Th>
                        <Th>{{ t('inventory.borrowed.colId') }}</Th>
                        <Th>{{ t('inventory.borrowed.colOwner') }}</Th>
                        <Th>{{ t('inventory.borrowed.colDue') }}</Th>
                    </THead>
                </thead>
                <tbody>
                    <TRow v-for="row in rows" :key="row.item.id">
                        <Td>{{ row.item.name }}</Td>
                        <Td muted>{{ row.item.internalId || '–' }}</Td>
                        <Td><PrimaryBadge>{{ row.ownerStationName }}</PrimaryBadge></Td>
                        <Td muted>{{ row.dueOn ? formatDate(row.dueOn) : t('inventory.borrowed.noDueDate') }}</Td>
                    </TRow>
                </tbody>
            </table>
        </div>
    </NeutralContainer>
</template>
