/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {StationFileFolder} from '@/api/generated/schema'
import MediaCrumb from './MediaCrumb.vue'

const props = defineProps<{
  breadcrumbs: StationFileFolder[]
  activeFolder: number | null
}>()

const emit = defineEmits<{
  (e: 'navigate', id: number | null): void
}>()

const {t} = useI18n()
</script>

<template>
  <nav class="flex items-center gap-1 text-sm flex-wrap">
    <MediaCrumb :folder-id="null" :current="props.activeFolder === null" @navigate="emit('navigate', $event)">
      <font-awesome-icon :icon="['fas', 'house']" class="mr-1"/>
      {{ t('stationPages.editor.root') }}
    </MediaCrumb>
    <template v-for="(b, i) in props.breadcrumbs" :key="b.id">
      <span class="text-(--text-muted)">/</span>
      <MediaCrumb :folder-id="b.id" :current="i === props.breadcrumbs.length - 1" @navigate="emit('navigate', $event)">
        {{ b.name }}
      </MediaCrumb>
    </template>
  </nav>
</template>
