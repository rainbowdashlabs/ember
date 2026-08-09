/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {InventoryContainer} from '@/api/inventoryContainers'

defineProps<{
  container: InventoryContainer
  path: string
  position: number
  total: number
  isLast: boolean
}>()

const emit = defineEmits<{
  prev: []
  next: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="mb-4">
    <div class="flex items-center justify-between gap-3 flex-wrap">
      <div>
        <SubHeader>{{ container.name }}</SubHeader>
        <div class="text-xs text-(--text-muted) mt-0.5">
          <font-awesome-icon :icon="['fas', 'location-dot']" class="mr-1.5" />
          {{ path }}
        </div>
        <div class="text-xs text-(--text-muted) mt-1">
          {{ t('inventory.checkContainer.walkPosition', {current: position, total}) }}
        </div>
      </div>
      <div class="flex gap-2">
        <SecondaryButton :disabled="position === 1" @click="emit('prev')">
          <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2" />
          {{ t('inventory.checkContainer.walkPrev') }}
        </SecondaryButton>
        <PrimaryButton v-if="!isLast" @click="emit('next')">
          {{ t('inventory.checkContainer.walkNext') }}
          <font-awesome-icon :icon="['fas', 'chevron-right']" class="ml-2" />
        </PrimaryButton>
      </div>
    </div>
    <div v-if="isLast" class="mt-3 text-sm text-(--text-muted)">
      {{ t('inventory.checkContainer.walkFinishHint') }}
    </div>
  </NeutralContainer>
</template>
