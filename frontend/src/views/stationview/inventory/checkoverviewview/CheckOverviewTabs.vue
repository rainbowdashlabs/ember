/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'

const activeTab = defineModel<'team' | 'member'>('activeTab', {required: true})

const {t} = useI18n()
</script>

<template>
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
</template>
