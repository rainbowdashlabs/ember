/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'

/** Switches the lending screen between what the partners offer and the requests in both directions. */
const activeTab = defineModel<'offers' | 'requests'>({required: true})

const {t} = useI18n()
</script>

<template>
  <div class="flex gap-2">
    <SelectionToggleButton :selected="activeTab === 'offers'" @toggle="activeTab = 'offers'">
      <font-awesome-icon :icon="['fas', 'boxes-stacked']" class="mr-1"/>
      {{ t('lending.tabs.offers') }}
    </SelectionToggleButton>
    <SelectionToggleButton :selected="activeTab === 'requests'" @toggle="activeTab = 'requests'">
      <font-awesome-icon :icon="['fas', 'list']" class="mr-1"/>
      {{ t('lending.tabs.requests') }}
    </SelectionToggleButton>
  </div>
</template>
