/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'

const {t} = useI18n()
</script>

<template>
  <div class="flex gap-2 mb-4">
    <SelectionToggleButton :selected="true">
      {{ t('apiStatus.slowest') }}
    </SelectionToggleButton>
    <SelectionToggleButton :selected="false">
      {{ t('apiStatus.fastest') }}
    </SelectionToggleButton>
    <SelectionToggleButton :selected="false">
      {{ t('apiStatus.failing') }}
    </SelectionToggleButton>
  </div>
</template>
