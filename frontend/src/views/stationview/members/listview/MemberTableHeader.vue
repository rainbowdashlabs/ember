/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import Th from '@/components/table/Th.vue'
import THead from '@/components/table/THead.vue'
import HeaderFilterCell from '@/components/table/HeaderFilterCell.vue'
import type {ProfileField} from '@/api/profileFields'

const {t} = useI18n()

type ColumnKey = 'name' | 'groups' | 'tags' | number

defineProps<{
  visibleColumns: ProfileField[]
  allSelected: boolean
  sortIcon: (column: 'name' | number) => string
  hasActiveFilter: (key: ColumnKey) => boolean
  openFilterModal: (key: ColumnKey, label: string) => void
  exportMode?: boolean
}>()

const emit = defineEmits<{
  toggleSort: [column: 'name' | number]
  toggleSelectAll: []
}>()
</script>

<template>
  <thead>
    <THead>
      <th v-if="exportMode" class="px-2 py-2 w-10">
        <CheckboxInput :model-value="allSelected" @update:model-value="emit('toggleSelectAll')"/>
      </th>
      <th v-if="!exportMode" class="px-3 py-2 w-20"></th>
      <Th>
        <HeaderFilterCell
            :label="t('membersList.colName')"
            :sort-icon="sortIcon('name')"
            :has-filter="hasActiveFilter('name')"
            show-sort
            show-filter
            @sort="emit('toggleSort', 'name')"
            @filter="openFilterModal('name', t('membersList.colName'))"
        />
      </Th>
      <Th>{{ t('membersList.colRole') }}</Th>
      <Th>{{ t('membersList.colEmail') }}</Th>
      <Th>
        <HeaderFilterCell
            :label="t('membersList.colGroups')"
            :has-filter="hasActiveFilter('groups')"
            show-filter
            @filter="openFilterModal('groups', t('membersList.colGroups'))"
        />
      </Th>
      <Th>
        <HeaderFilterCell
            :label="t('membersList.colTags')"
            :has-filter="hasActiveFilter('tags')"
            show-filter
            @filter="openFilterModal('tags', t('membersList.colTags'))"
        />
      </Th>
      <Th v-for="field in visibleColumns" :key="field.id">
        <HeaderFilterCell
            :label="field.name ?? ''"
            :sort-icon="sortIcon(field.id)"
            :has-filter="hasActiveFilter(field.id)"
            show-sort
            show-filter
            @sort="emit('toggleSort', field.id)"
            @filter="openFilterModal(field.id, field.name ?? '')"
        />
      </Th>
    </THead>
  </thead>
</template>
