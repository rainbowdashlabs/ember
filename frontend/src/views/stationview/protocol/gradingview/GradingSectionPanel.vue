/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import GradingItemButton from './GradingItemButton.vue'
import type { TestProtocolSection, TestProtocolItem } from '@/api/protocol'

defineProps<{
  section: TestProtocolSection
  childSections: TestProtocolSection[]
  sectionItems: (sectionId: number) => TestProtocolItem[]
  checks: Map<number, boolean>
  score: number
  maxPoints: number
  done: boolean
}>()

defineEmits<{
  (e: 'toggleCheck', itemId: number): void
  (e: 'toggleDone'): void
}>()

const { t } = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3 mb-4">
    <div class="flex items-center justify-between">
      <SectionHeader class="font-bold">
        <font-awesome-icon v-if="done" :icon="['fas', 'circle-check']" class="w-4 h-4 text-[var(--success)] mr-1" />
        {{ section.name }}
      </SectionHeader>
      <div class="flex items-center gap-2">
        <SuccessBadge>{{ score }} / {{ maxPoints }}P</SuccessBadge>
        <IconButton v-if="!done" icon="check" :label="t('protocol.markDone')" @click="$emit('toggleDone')" />
        <IconButton v-else icon="rotate-left" :label="t('protocol.unmarkDone')" @click="$emit('toggleDone')" />
      </div>
    </div>

    <GradingItemButton
      v-for="item in sectionItems(section.id)"
      :key="item.id"
      :item="item"
      :checked="!!checks.get(item.id)"
      @toggle="$emit('toggleCheck', item.id)"
    />

    <template v-for="sub in childSections" :key="sub.id">
      <div class="border-t border-[var(--border)] pt-3 mt-3">
        <SubHeader class="text-sm mb-2">{{ sub.name }}</SubHeader>
        <div class="space-y-2">
          <GradingItemButton
            v-for="item in sectionItems(sub.id)"
            :key="item.id"
            :item="item"
            :checked="!!checks.get(item.id)"
            @toggle="$emit('toggleCheck', item.id)"
          />
        </div>
      </div>
    </template>
  </NeutralContainer>
</template>
