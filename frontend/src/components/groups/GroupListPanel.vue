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
import ColorDot from '@/components/display/ColorDot.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {MemberGroupSet} from '@/api/generated/schema'
import {boundTypeNames, type GroupRow} from '@/util/groupRules'
import {useGroupsCapabilities} from '@/composables/useGroupsConfig'

const {t} = useI18n()
const capabilities = useGroupsCapabilities()

const props = defineProps<{
  groups: GroupRow[]
  selectedGroup: GroupRow | null
  canConvertToTag: boolean
  /** The sets the groups can belong to, where the owner keeps any; a group in one shows its name. */
  sets?: MemberGroupSet[]
}>()

const emit = defineEmits<{
  create: []
  select: [group: GroupRow]
  edit: [group: GroupRow]
  delete: [group: GroupRow]
  convert: [group: GroupRow]
}>()

function setName(group: GroupRow): string | undefined {
  return props.sets?.find(set => set.id === group.groupSetId)?.name
}

function boundTo(group: GroupRow): string {
  return t('memberEdit.groupBoundTo', {types: boundTypeNames(group)})
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('memberGroups.title') }}</SubHeader>
      <PrimaryButton v-if="capabilities.canEdit" data-onboarding="groups.add" :icon="['fas', 'plus']" @click="emit('create')">
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
        <span class="flex items-center gap-2 flex-wrap">
          <ColorDot v-if="group.color" :color="group.color"/>
          <span class="font-medium">{{ group.name }}</span>
          <InfoBadge v-if="setName(group)">{{ setName(group) }}</InfoBadge>
          <SecondaryBadge v-if="group.userTypes?.length">{{ boundTo(group) }}</SecondaryBadge>
        </span>
        <div class="flex items-center gap-2">
          <MutedIconButton v-if="canConvertToTag" :icon="['fas', 'hashtag']" :label="t('memberGroups.convertToTag')" @click.stop="emit('convert', group)"/>
          <EditButton v-if="capabilities.canEdit" @click.stop="emit('edit', group)"/>
          <DeleteButton v-if="capabilities.canEdit" @click.stop="emit('delete', group)"/>
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>
