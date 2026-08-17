/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {TableEntry} from '@/api/dataTracking'
import IconButton from '@/components/button/IconButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

defineProps<{
  name: string
  entry: TableEntry
}>()

const emit = defineEmits<{
  close: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="sticky top-0 z-10 bg-(--bg) border-b border-(--border) p-4 flex items-center justify-between gap-2">
    <div class="min-w-0 flex-1">
      <SubHeader class="!text-xl font-mono !mb-0 truncate">{{ name }}</SubHeader>
      <p v-if="entry.description" class="mt-1 text-sm text-(--text-muted) italic">
        {{ entry.description }}
      </p>
      <div class="flex items-center gap-2 mt-1 text-xs text-(--text-muted) flex-wrap">
        <span>{{ entry.feature ?? '-' }}</span>
        <span>·</span>
        <span>{{ entry.scope ?? '-' }}</span>
        <span>·</span>
        <span class="font-mono">{{ entry.tableHash.slice(0, 20) }}…</span>
      </div>
    </div>
    <IconButton :icon="['fas', 'xmark']" :label="t('common.close')" @click="emit('close')"/>
  </div>
</template>
