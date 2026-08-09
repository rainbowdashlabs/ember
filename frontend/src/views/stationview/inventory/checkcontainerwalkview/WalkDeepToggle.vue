/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const deep = defineModel<boolean>('deep', {required: true})

const emit = defineEmits<{
  change: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center justify-end mb-4 gap-3 flex-wrap">
    <label class="flex items-center gap-2 text-sm">
      <ToggleInput v-model="deep" @update:model-value="emit('change')"/>
      <span>{{ t('inventory.checkContainer.deep') }}</span>
    </label>
  </div>
</template>
