/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import type { ProcurementEntry } from '@/api/procurement'

const props = withDefaults(defineProps<{
  entries: ProcurementEntry[]
  readonly?: boolean
  canCreate?: boolean
}>(), {
  readonly: false,
  canCreate: false,
})

const emit = defineEmits<{
  fulfill: [id: number]
  create: []
}>()

const { t } = useI18n()
</script>

<template>
  <template v-if="props.entries.length > 0">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <SubHeader>
        <font-awesome-icon :icon="['fas', 'folder-plus']" class="mr-2" />
        {{ t('inventory.detail.procurement') }} ({{ props.entries.length }})
      </SubHeader>
      <PrimaryButton v-if="props.canCreate" :icon="['fas', 'folder-plus']" @click="emit('create')">
        {{ t('inventory.detail.createProcurement') }}
      </PrimaryButton>
    </div>
    <DataTable>
      <template #head>
        <Th>{{ t('inventory.detail.owner') }}</Th>
        <Th>{{ t('inventory.detail.size') }}</Th>
        <Th>{{ t('inventory.detail.notes') }}</Th>
        <th v-if="!props.readonly" class="px-3 py-2"></th>
      </template>
      <TRow v-for="p in props.entries" :key="p.id">
        <Td><MemberName :identity="p.memberIdentity ?? null"/></Td>
        <Td><SizeBadge>{{ p.sizeLabel || t('common.unisize') }}</SizeBadge></Td>
        <Td muted>{{ p.notes || '-' }}</Td>
        <Td v-if="!props.readonly" align="right">
          <PrimaryButton :icon="['fas', 'check']" @click="emit('fulfill', p.id)">
            {{ t('procurement.markFulfilled') }}
          </PrimaryButton>
        </Td>
      </TRow>
    </DataTable>
  </template>
</template>
