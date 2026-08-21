/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import BaseButton from '@/components/button/BaseButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {StationFileFolder, StationFileTag} from '@/api/media'

const activeFolder = defineModel<number | null>('activeFolder', {required: true})
const activeTagFilter = defineModel<number | null>('activeTagFilter', {required: true})

const props = defineProps<{
  folders: StationFileFolder[]
  tags: StationFileTag[]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-center gap-2 text-xs">
    <SelectInput :model-value="activeFolder === null ? '' : String(activeFolder)"
                 class="!px-2 !py-1"
                 @update:model-value="(v: string | number | null | undefined) => activeFolder = v ? +v : null">
      <option value="">{{ t('stationPages.editor.allFiles') }}</option>
      <option v-for="f in props.folders" :key="f.id" :value="f.id">{{ f.name }}</option>
    </SelectInput>
    <BaseButton compact class="!rounded-full !border !font-normal"
                :class="activeTagFilter === null ? '!border-primary !text-primary' : '!border-(--border) !text-(--text-muted)'"
                @click="activeTagFilter = null">
      {{ t('stationPages.editor.allTags') }}
    </BaseButton>
    <BaseButton v-for="tag in props.tags" :key="tag.id" compact
                class="!rounded-full !border !font-normal"
                :style="activeTagFilter === tag.id ? {borderColor: tag.color ?? 'var(--primary)', color: tag.color ?? 'var(--primary)'} : {}"
                :class="activeTagFilter === tag.id ? '' : '!border-(--border) !text-(--text-muted)'"
                @click="activeTagFilter = activeTagFilter === tag.id ? null : tag.id">
      <span class="inline-block w-2 h-2 rounded-full mr-1" :style="{background: tag.color ?? '#888'}"/>
      {{ tag.name }}
    </BaseButton>
  </div>
</template>
