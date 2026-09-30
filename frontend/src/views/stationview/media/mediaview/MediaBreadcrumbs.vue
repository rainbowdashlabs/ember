/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import BaseButton from '@/components/button/BaseButton.vue'
import type {StationFileFolder} from '@/api/media'

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
    <BaseButton compact class="!font-normal hover:bg-(--bg-accent)"
                :class="props.activeFolder === null ? '!text-primary !font-medium' : '!text-(--text-muted)'"
                @click="emit('navigate', null)">
      <font-awesome-icon :icon="['fas', 'house']" class="mr-1"/>
      {{ t('stationPages.editor.root') }}
    </BaseButton>
    <template v-for="(b, i) in props.breadcrumbs" :key="b.id">
      <span class="text-(--text-muted)">/</span>
      <BaseButton compact class="!font-normal hover:bg-(--bg-accent)"
                  :class="i === props.breadcrumbs.length - 1 ? '!text-primary !font-medium' : '!text-(--text-muted)'"
                  @click="emit('navigate', b.id)">
        {{ b.name }}
      </BaseButton>
    </template>
  </nav>
</template>
