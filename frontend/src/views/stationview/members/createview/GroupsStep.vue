/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {MemberGroup} from '@/api/types'

const {t} = useI18n()

defineProps<{
  groups: MemberGroup[]
  selectedIds: Set<number>
  submitLabel: string
}>()

const emit = defineEmits<{
  next: []
  back: []
  toggle: [id: number]
}>()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('membersCreate.stepGroups') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('membersCreate.stepGroupsHint') }}</p>

    <div v-if="groups.length === 0" class="text-center text-(--text-muted) py-4">
      {{ t('membersCreate.noGroups') }}
    </div>

    <div class="space-y-2">
      <div
          v-for="group in groups"
          :key="group.id"
          :class="selectedIds.has(group.id) ? 'border-primary bg-primary/10 ring-2 ring-primary/30' : 'border-bg-light-accent dark:border-bg-dark-accent hover:border-primary'"
          class="flex items-center justify-between rounded-lg px-4 py-3 border cursor-pointer transition-colors"
          @click="emit('toggle', group.id)"
      >
        <span class="font-medium">{{ group.name }}</span>
        <font-awesome-icon v-if="selectedIds.has(group.id)" :icon="['fas', 'check']" class="text-primary"/>
      </div>
    </div>

    <div class="flex justify-between">
      <SecondaryButton @click="emit('back')">{{ t('membersCreate.back') }}</SecondaryButton>
      <PrimaryButton @click="emit('next')">{{ submitLabel }}</PrimaryButton>
    </div>
  </NeutralContainer>
</template>
