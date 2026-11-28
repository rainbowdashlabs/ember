/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import type { InventoryItem } from '@/api/generated/schema'
import type { DataTableApi } from '@/composables/useDataTable'

defineProps<{
  table: DataTableApi<InventoryItem>
  count: number
  showQuickAssign: boolean
  showAdd: boolean
  /** Whether this screen offers writing a whole inventory down at once. The association's does not. */
  showIntake?: boolean
  showSearch: boolean
  /** Whether many pieces may be changed at once here, which offers the select mode. */
  showSelect?: boolean
  /** Whether the select mode is on. */
  selecting?: boolean
}>()

const emit = defineEmits<{
  quickAssign: []
  add: []
  intake: []
  toggleSelect: []
  selectAllShown: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-2">
    <SubHeader>{{ t('inventory.edit.itemsTitle') }} ({{ count }})</SubHeader>
    <ButtonRow v-if="showQuickAssign || showAdd || showIntake" align="end">
      <SecondaryButton v-if="showIntake" :icon="['fas', 'table-columns']" data-testid="open-intake"
                       @click="emit('intake')">
        {{ t('inventory.intake.open') }}
      </SecondaryButton>
      <PrimaryButton v-if="showQuickAssign" :icon="['fas', 'user-plus']" @click="emit('quickAssign')">
        {{ t('inventory.edit.quickAssign') }}
      </PrimaryButton>
      <PrimaryButton v-if="showAdd" :icon="['fas', 'plus']" @click="emit('add')">
        {{ t('inventory.edit.addItem') }}
      </PrimaryButton>
    </ButtonRow>
  </div>
  <slot/>
  <div v-if="showSearch" class="flex items-center gap-2">
    <TextInput :model-value="table.search" :placeholder="t('inventory.edit.searchItems')" class="flex-1" @update:model-value="table.searchFor($event ?? '')"/>
    <TableColumnPicker :table="table"/>
  </div>
  <ButtonRow v-if="showSelect" align="end">
    <SecondaryButton
        v-if="selecting"
        :icon="['fas', 'check-double']"
        data-testid="items-select-shown"
        @click="emit('selectAllShown')"
    >
      {{ t('inventory.batch.selectShown') }}
    </SecondaryButton>
    <SecondaryButton
        :icon="['fas', selecting ? 'xmark' : 'list-check']"
        :aria-pressed="selecting"
        data-testid="items-select-mode"
        @click="emit('toggleSelect')"
    >
      {{ selecting ? t('inventory.batch.stop') : t('inventory.batch.start') }}
    </SecondaryButton>
  </ButtonRow>
</template>
