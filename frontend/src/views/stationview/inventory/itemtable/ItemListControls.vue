/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ColumnPickerButton from '@/components/table/ColumnPickerButton.vue'
import type { ItemTableApi } from './useItemTable'

defineProps<{
  table: ItemTableApi
  count: number
  showQuickAssign: boolean
  showAdd: boolean
  /** Whether this screen offers writing a whole inventory down at once. The association's does not. */
  showIntake?: boolean
  showSearch: boolean
}>()

const emit = defineEmits<{
  quickAssign: []
  add: []
  intake: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-2">
    <SubHeader>{{ t('inventory.edit.itemsTitle') }} ({{ count }})</SubHeader>
    <div v-if="showQuickAssign || showAdd || showIntake" class="flex items-center gap-2">
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
    </div>
  </div>
  <slot/>
  <div v-if="showSearch" class="flex items-center gap-2">
    <TextInput v-model="table.searchText" :placeholder="t('inventory.edit.searchItems')" class="flex-1"/>
    <ColumnPickerButton :options="table.pickerOptions" @toggle="table.toggleColumn($event)"/>
  </div>
</template>
