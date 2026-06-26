/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

defineProps<{
  label: string
  hint: string
  app: boolean
  email: boolean
  feed: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div class="grid grid-cols-[1fr_auto_auto_auto] gap-x-4 items-center py-1">
    <div>
      <span class="text-sm font-medium">{{ t(label) }}</span>
      <p class="text-xs text-(--text-muted)">{{ t(hint) }}</p>
    </div>
    <div class="w-12 flex justify-center"><ToggleInput :model-value="app" disabled/></div>
    <div class="w-12 flex justify-center"><ToggleInput :model-value="email" disabled/></div>
    <div class="w-12 flex justify-center"><ToggleInput :model-value="feed" disabled/></div>
  </div>
</template>
