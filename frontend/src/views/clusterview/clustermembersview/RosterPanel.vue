/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {ClusterMemberSummary} from '@/api/clusterMembers'

/** Who runs the association. The list the page opens on, rather than a form. */
const props = defineProps<{
  members: readonly ClusterMemberSummary[]
  selectedId: number | null
  editable: boolean
}>()

const emit = defineEmits<{
  select: [member: ClusterMemberSummary]
  add: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t('clusterMembers.rosterTitle') }}</SectionHeader>
      <PrimaryButton v-if="props.editable" :icon="['fas', 'plus']" @click="emit('add')">
        {{ t('clusterMembers.add') }}
      </PrimaryButton>
    </div>

    <EmptyState v-if="props.members.length === 0">{{ t('clusterMembers.empty') }}</EmptyState>

    <div v-else class="space-y-2">
      <button
          v-for="member in props.members"
          :key="member.id"
          data-testid="roster-row"
          class="w-full rounded-theme border px-3 py-2 text-left transition-colors"
          :class="member.id === props.selectedId
            ? 'border-primary'
            : 'border-(--border) hover:border-primary'"
          @click="emit('select', member)"
      >
        <div class="flex items-center justify-between gap-2">
          <span class="font-medium">{{ member.name ?? member.email }}</span>
          <SecondaryBadge>{{ t(`clusterOverview.role.${member.userType}`) }}</SecondaryBadge>
        </div>
        <MutedText v-if="member.name && member.email" size="sm">{{ member.email }}</MutedText>
      </button>
    </div>
  </NeutralContainer>
</template>
