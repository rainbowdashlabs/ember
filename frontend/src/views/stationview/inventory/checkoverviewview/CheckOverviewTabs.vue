/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import IconButton from '@/components/button/IconButton.vue'

const activeTab = defineModel<'team' | 'member'>('activeTab', {required: true})
const sortBy = defineModel<'name' | 'lastChecked'>('sortBy', {required: true})

const {t} = useI18n()

function toggleSort() {
  sortBy.value = sortBy.value === 'name' ? 'lastChecked' : 'name'
}
</script>

<template>
  <div class="flex items-center justify-between gap-4">
    <div class="flex gap-2">
      <SelectionToggleButton
          :selected="activeTab === 'team'"
          size="md"
          @toggle="activeTab = 'team'"
      >
        {{ t('inventory.check.tabTeam') }}
      </SelectionToggleButton>
      <SelectionToggleButton
          :selected="activeTab === 'member'"
          size="md"
          @toggle="activeTab = 'member'"
      >
        {{ t('inventory.check.tabMember') }}
      </SelectionToggleButton>
    </div>
    <IconButton
        :icon="['fas', 'sort']"
        :label="sortBy === 'name' ? t('inventory.check.sortByLastChecked') : t('inventory.check.sortByName')"
        class="text-xs text-(--text-muted) hover:text-primary"
        @click="toggleSort"
    />
  </div>
</template>
