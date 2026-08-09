/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {TemplateGroupEntry} from '@/api/attendance'
import type {MemberGroup} from '@/api/types'

const props = defineProps<{
  groups: TemplateGroupEntry[]
  availableGroups: MemberGroup[]
}>()

const emit = defineEmits<{
  add: [groupId: number]
  remove: [groupId: number]
  moveUp: [index: number]
  moveDown: [index: number]
}>()

const {t} = useI18n()

function groupName(groupId: number): string {
  return props.availableGroups.find(g => g.id === groupId)?.name ?? `#${groupId}`
}

const unselectedGroups = computed(() => {
  const selectedIds = new Set(props.groups.map(g => g.groupId))
  return props.availableGroups.filter(g => !selectedIds.has(g.id))
})
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('attendanceConfig.groups') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('attendanceConfig.groupsHint') }}</p>

    <EmptyState compact v-if="groups.length === 0">{{ t('attendanceConfig.noGroups') }}</EmptyState>

    <div class="space-y-2">
      <NeutralContainer v-for="(group, index) in groups" :key="group.groupId" class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span class="text-xs text-(--text-muted) w-6 text-center">{{ index + 1 }}</span>
          <span class="font-medium">{{ groupName(group.groupId) }}</span>
        </div>
        <div class="flex items-center gap-1">
          <IconButton :disabled="index === 0" :icon="['fas', 'chevron-up']"
                     :label="t('common.moveUp')" @click="emit('moveUp', index)"/>
          <IconButton :disabled="index === groups.length - 1" :icon="['fas', 'chevron-down']"
                     :label="t('common.moveDown')" @click="emit('moveDown', index)"/>
          <DeleteButton @click="emit('remove', group.groupId)"/>
        </div>
      </NeutralContainer>
    </div>

    <div v-if="unselectedGroups.length > 0" class="pt-2">
      <FieldLabel class="mb-1">{{ t('attendanceConfig.addGroup') }}</FieldLabel>
      <div class="flex flex-wrap gap-2">
        <SecondaryButton :icon="['fas', 'plus']" v-for="group in unselectedGroups" :key="group.id"
                         @click="emit('add', group.id)">
          {{ group.name }}
        </SecondaryButton>
      </div>
    </div>
  </NeutralContainer>
</template>
