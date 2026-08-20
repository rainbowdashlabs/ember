/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'

const props = defineProps<{
  selectedCount: number
}>()

const emit = defineEmits<{
  (e: 'move'): void
  (e: 'delete'): void
  (e: 'clear'): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer v-if="props.selectedCount > 0" class="flex flex-wrap items-center gap-2 !py-2">
    <span class="text-sm">{{ t('stationPages.editor.selectedCount', {count: props.selectedCount}) }}</span>
    <SecondaryButton @click="emit('move')">
      <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="mr-1"/>
      {{ t('stationPages.editor.moveSelected') }}
    </SecondaryButton>
    <ErrorButton @click="emit('delete')">
      <font-awesome-icon :icon="['fas', 'trash']" class="mr-1"/>
      {{ t('stationPages.editor.deleteSelected') }}
    </ErrorButton>
    <SecondaryButton @click="emit('clear')">{{ t('stationPages.editor.clearSelection') }}</SecondaryButton>
  </NeutralContainer>
</template>
