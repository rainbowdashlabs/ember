/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MemberName from '@/components/avatar/MemberName.vue'
import IconButton from '@/components/button/IconButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {AssignableMember} from '@/composables/useGroupsConfig'

const {t} = useI18n()

defineProps<{
  sortedGroupMembers: AssignableMember[]
  availableMembers: AssignableMember[]
}>()

const emit = defineEmits<{
  (e: 'add', member: AssignableMember): void
  (e: 'remove', member: AssignableMember): void
}>()
</script>

<template>
  <div class="space-y-1">
    <FieldLabel class="text-(--text-muted)">{{ t('memberGroups.currentMembers') }}</FieldLabel>
    <MutedText tag="div" size="sm" class="py-2" v-if="sortedGroupMembers.length === 0">
      {{ t('memberGroups.noMembers') }}
    </MutedText>
    <div class="space-y-1">
      <div v-for="member in sortedGroupMembers" :key="member.id"
           class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
        <div>
          <MemberName :identity="member.identity" class="text-sm font-medium"/>
          <MutedText class="ml-2" v-if="member.name && member.email">{{ member.email }}</MutedText>
        </div>
        <IconButton :icon="['fas', 'xmark']" :label="t('memberGroups.removeMember')" class="text-error hover:text-error/80 text-sm" @click="emit('remove', member)"/>
      </div>
    </div>
  </div>

  <div class="space-y-1">
    <FieldLabel class="text-(--text-muted)">{{ t('memberGroups.addMembers') }}</FieldLabel>
    <MutedText tag="div" size="sm" class="py-2" v-if="availableMembers.length === 0">
      {{ t('memberGroups.allAdded') }}
    </MutedText>
    <div class="space-y-1">
      <div
          v-for="member in availableMembers"
          :key="member.id"
          data-testid="group-candidate"
          class="flex items-center justify-between rounded-lg px-3 py-2 hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent cursor-pointer transition-colors"
          @click="emit('add', member)"
      >
        <div>
          <MemberName :identity="member.identity" class="text-sm font-medium"/>
          <MutedText class="ml-2" v-if="member.name && member.email">{{ member.email }}</MutedText>
        </div>
        <font-awesome-icon :icon="['fas', 'plus']" class="text-primary text-sm"/>
      </div>
    </div>
  </div>
</template>
