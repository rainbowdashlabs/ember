/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type {TileCacheStats} from '@/api/maps'

const {t} = useI18n()

defineProps<{
  cacheStats: TileCacheStats | null
}>()

const tileCacheMaxMb = defineModel<number>({required: true})

const emit = defineEmits<{
  requestPurge: []
}>()

function formatMb(bytes: number): string {
  return (bytes / (1024 * 1024)).toFixed(1)
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('adminMaps.cacheSection') }}</SubHeader>
    <div class="space-y-1">
      <FieldLabel>{{ t('adminMaps.cacheMaxMb') }}</FieldLabel>
      <NumberInput v-model="tileCacheMaxMb" :min="0" :max="10000"/>
    </div>
    <div v-if="cacheStats" class="space-y-1">
      <FieldLabel>{{ t('adminMaps.cacheStatsLabel') }}</FieldLabel>
      <p class="text-sm">
        {{
          t('adminMaps.cacheStatsValue', {
            tiles: cacheStats.tiles,
            used: formatMb(cacheStats.bytes),
            max: formatMb(cacheStats.maxBytes),
          })
        }}
      </p>
    </div>
    <DeleteButton @click="emit('requestPurge')">
      {{ t('adminMaps.cachePurge') }}
    </DeleteButton>
  </NeutralContainer>
</template>
