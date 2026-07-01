/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
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
    <div class="flex-1 min-w-[200px] max-w-md">
      <SearchInput v-model="search" :placeholder="t('checklist.searchPlaceholder')" autofocus/>
    </div>
    <label class="flex items-center gap-2 text-sm" v-if="removedCount > 0">
      <ToggleInput v-model="showRemoved"/>
      <span>{{ t('checklist.showRemoved') }} ({{ removedCount }})</span>
    </label>
  </div>
</template>
