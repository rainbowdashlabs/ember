/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'

/**
 * A link that points somewhere known, a file or a page of the station, shown by its name instead
 * of its address. Clearing it gives the field back for typing.
 */
defineProps<{
  icon: string[]
  title: string
  hint: string
}>()

const emit = defineEmits<{
  clear: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex-1 flex items-center gap-2 px-3 py-2 rounded-theme border border-(--border) bg-bg-light dark:bg-bg-dark">
    <font-awesome-icon :icon="icon" class="text-primary shrink-0"/>
    <span class="flex flex-col min-w-0 flex-1">
      <span class="text-sm truncate" :title="title">{{ title }}</span>
      <span class="text-xs text-(--text-muted) truncate">{{ hint }}</span>
    </span>
    <IconButton
        :icon="['fas', 'xmark']"
        :label="t('common.delete')"
        class="text-(--text-muted) hover:text-error shrink-0 !p-1"
        @click="emit('clear')"
    />
  </div>
</template>
