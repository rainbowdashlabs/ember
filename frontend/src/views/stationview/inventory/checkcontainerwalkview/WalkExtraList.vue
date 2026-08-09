/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import IconButton from '@/components/button/IconButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type {ExtraRow} from './types'

defineProps<{
  rows: ExtraRow[]
}>()

const emit = defineEmits<{
  remove: [itemId: number]
}>()

const {t} = useI18n()
</script>

<template>
  <SectionHeader>{{ t('inventory.checkContainer.extra') }}</SectionHeader>
  <NeutralContainer class="mb-4">
    <ul class="divide-y divide-(--bg-accent)">
      <li v-for="row in rows" :key="row.item.id" class="py-2 flex items-center gap-3">
        <span class="flex-1">
          <span class="font-medium">{{ row.item.name }}</span>
          <span v-if="row.item.internalId" class="text-xs text-(--text-muted) ml-2">{{ row.item.internalId }}</span>
        </span>
        <InfoBadge>{{ t('inventory.checkContainer.statusExtra') }}</InfoBadge>
        <IconButton :icon="['fas', 'trash']" :label="t('inventory.checkContainer.removeExtra')" @click="emit('remove', row.item.id)" />
      </li>
    </ul>
  </NeutralContainer>
</template>
