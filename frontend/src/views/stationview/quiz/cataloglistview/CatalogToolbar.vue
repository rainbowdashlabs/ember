/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const searchQuery = defineModel<string>('searchQuery', {required: true})
const showFederated = defineModel<boolean>('showFederated', {required: true})
const filterStationId = defineModel<string | null>('filterStationId', {required: true})

defineProps<{
  partnerStations: { id: string; name: string }[]
}>()

const emit = defineEmits<{
  openCreateModal: []
  triggerImport: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex items-center justify-between flex-wrap gap-2">
    <SubHeader>{{ t('quiz.catalogs.title') }}</SubHeader>
    <div class="flex gap-2 flex-wrap">
      <SecondaryButton :icon="['fas', 'file-import']" @click="emit('triggerImport')">
        {{ t('quiz.catalogs.import') }}
      </SecondaryButton>
      <PrimaryButton :icon="['fas', 'plus']" @click="emit('openCreateModal')">
        {{ t('quiz.catalogs.create') }}
      </PrimaryButton>
    </div>
  </div>

  <div>
    <SearchInput
      v-model="searchQuery"
      :placeholder="t('quiz.catalogs.search')"
    />
  </div>

  <div class="flex flex-wrap items-center gap-2">
    <SelectionToggleButton :selected="showFederated" @toggle="showFederated = !showFederated">
      <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="w-3 h-3 mr-1" />
      {{ t('federation.shared') }}
    </SelectionToggleButton>
    <SelectInput
      v-if="showFederated && partnerStations.length > 0"
      :model-value="filterStationId != null ? String(filterStationId) : ''"
      class="!w-auto !text-xs !py-1"
      @update:model-value="(v: string | number | null | undefined) => filterStationId = v ? String(v) : null"
    >
      <option value="">{{ t('quiz.catalogs.allStations') }}</option>
      <option v-for="station in partnerStations" :key="station.id" :value="String(station.id)">
        {{ station.name }}
      </option>
    </SelectInput>
  </div>
</template>
