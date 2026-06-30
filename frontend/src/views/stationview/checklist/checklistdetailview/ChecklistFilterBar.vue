/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const search = defineModel<string>('search', {required: true})
const showRemoved = defineModel<boolean>('showRemoved', {required: true})

defineProps<{
  removedCount: number
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-center gap-3 mb-3">
    <div class="relative flex-1 min-w-[200px] max-w-md">
      <TextInput v-model="search" :placeholder="t('checklist.searchPlaceholder')"/>
      <IconButton
          v-if="search"
          :icon="['fas', 'xmark']"
          :label="t('checklist.memberSearchClear')"
          class="absolute right-1 top-1/2 -translate-y-1/2"
          @click="search = ''"
      />
    </div>
    <label class="flex items-center gap-2 text-sm" v-if="removedCount > 0">
      <ToggleInput v-model="showRemoved"/>
      <span>{{ t('checklist.showRemoved') }} ({{ removedCount }})</span>
    </label>
  </div>
</template>
