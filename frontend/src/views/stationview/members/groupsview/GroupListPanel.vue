/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {MemberGroup} from '@/api/types'

const {t} = useI18n()

defineProps<{
  groups: MemberGroup[]
  selectedGroup: MemberGroup | null
  canConvertToTag: boolean
}>()

const emit = defineEmits<{
  (e: 'create'): void
  (e: 'select', group: MemberGroup): void
  (e: 'edit', group: MemberGroup): void
  (e: 'delete', group: MemberGroup): void
  (e: 'convert', group: MemberGroup): void
}>()
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('memberGroups.title') }}</SubHeader>
      <PrimaryButton :icon="['fas', 'plus']" @click="emit('create')">
        {{ t('memberGroups.create') }}
      </PrimaryButton>
    </div>

    <EmptyState v-if="groups.length === 0">{{ t('memberGroups.empty') }}</EmptyState>

    <div class="space-y-2">
      <NeutralContainer
          v-for="group in groups"
          :key="group.id"
          :class="selectedGroup?.id === group.id ? 'border-primary' : 'hover:border-primary'"
          class="flex items-center justify-between gap-2 flex-wrap cursor-pointer transition-colors"
          @click="emit('select', group)"
      >
        <span class="flex items-center gap-2">
          <span v-if="group.color" class="inline-block h-3 w-3 rounded-full" :style="{ backgroundColor: group.color }"></span>
          <span class="font-medium">{{ group.name }}</span>
        </span>
        <div class="flex items-center gap-2">
          <MutedIconButton v-if="canConvertToTag" :icon="['fas', 'hashtag']" :label="t('memberGroups.convertToTag')" @click.stop="emit('convert', group)"/>
          <EditButton @click.stop="emit('edit', group)"/>
          <DeleteButton @click.stop="emit('delete', group)"/>
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>
