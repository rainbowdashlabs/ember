/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MemberName from '@/components/avatar/MemberName.vue'
import IconButton from '@/components/button/IconButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {useGroupsCapabilities, type AssignableMember} from '@/composables/useGroupsConfig'

const {t} = useI18n()

defineProps<{
  sortedGroupMembers: AssignableMember[]
  availableMembers: AssignableMember[]
}>()

const emit = defineEmits<{
  (e: 'add', member: AssignableMember): void
  (e: 'remove', member: AssignableMember): void
}>()

/** The panel is one panel, but a list of stations is not called a list of members. */
const capabilities = useGroupsCapabilities()
const words = computed(() => capabilities.holds === 'stations'
    ? {
      current: t('clusterStationGroups.currentStations'),
      none: t('clusterStationGroups.noStations'),
      add: t('clusterStationGroups.addStations'),
      remove: t('clusterStationGroups.removeStation'),
      allAdded: t('clusterStationGroups.allAdded'),
    }
    : {
      current: t('memberGroups.currentMembers'),
      none: t('memberGroups.noMembers'),
      add: t('memberGroups.addMembers'),
      remove: t('memberGroups.removeMember'),
      allAdded: t('memberGroups.allAdded'),
    })
</script>

<template>
  <div class="space-y-1">
    <FieldLabel class="text-(--text-muted)">{{ words.current }}</FieldLabel>
    <MutedText tag="div" size="sm" class="py-2" v-if="sortedGroupMembers.length === 0">
      {{ words.none }}
    </MutedText>
    <div class="space-y-1">
      <div v-for="member in sortedGroupMembers" :key="member.id"
           class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
        <div>
          <MemberName :identity="member.identity" class="text-sm font-medium"/>
          <MutedText class="ml-2" v-if="member.name && member.email">{{ member.email }}</MutedText>
        </div>
        <IconButton :icon="['fas', 'xmark']" :label="words.remove" class="text-error hover:text-error/80 text-sm" @click="emit('remove', member)"/>
      </div>
    </div>
  </div>

  <div class="space-y-1">
    <FieldLabel class="text-(--text-muted)">{{ words.add }}</FieldLabel>
    <MutedText tag="div" size="sm" class="py-2" v-if="availableMembers.length === 0">
      {{ words.allAdded }}
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
