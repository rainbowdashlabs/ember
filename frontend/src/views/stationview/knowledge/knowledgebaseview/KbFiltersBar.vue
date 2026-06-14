/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {KbTag} from '@/api/knowledgeBase'

const showFederated = defineModel<boolean>('showFederated', {required: true})
const filterStationId = defineModel<string | null>('filterStationId', {required: true})
const filterTag = defineModel<string>('filterTag', {required: true})

defineProps<{
  partnerStations: {id: string; name: string}[]
  allKbTags: KbTag[]
}>()

const emit = defineEmits<{
  refresh: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-center gap-2 mb-4">
    <SelectionToggleButton :selected="showFederated" @toggle="showFederated = !showFederated; emit('refresh')">
      <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="w-3 h-3 mr-1"/>
      {{ t('federation.shared') }}
    </SelectionToggleButton>
    <SelectInput
        v-if="showFederated && partnerStations.length > 0"
        :model-value="filterStationId != null ? String(filterStationId) : ''"
        class="!w-auto !text-xs !py-1"
        @update:model-value="(v: string | undefined) => { filterStationId = v || null; emit('refresh') }"
    >
      <option value="">{{ t('kb.allStations') }}</option>
      <option v-for="station in partnerStations" :key="station.id" :value="String(station.id)">
        {{ station.name }}
      </option>
    </SelectInput>
    <SelectInput v-if="allKbTags.length > 0" v-model="filterTag" class="!w-auto !text-xs !py-1" @change="emit('refresh')">
      <option value="">{{ t('kb.allTags') }}</option>
      <option v-for="tag in allKbTags" :key="tag.id" :value="tag.name">
        {{ tag.name }}
      </option>
    </SelectInput>
  </div>
</template>
