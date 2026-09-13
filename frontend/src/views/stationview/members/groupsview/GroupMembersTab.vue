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
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import GroupStationPicker from './GroupStationPicker.vue'
import {useMemberPick} from '@/composables/useMemberPick'
import {fromMember, identityOf, type MemberOption} from '@/components/input/select/memberOption'
import {useGroupsCapabilities, type AssignableMember} from '@/composables/useGroupsConfig'

const {t} = useI18n()

const props = defineProps<{
  sortedGroupMembers: AssignableMember[]
  availableMembers: MemberOption[]
  /** The kinds of member still on offer, for the filter beside the search. */
  offeredUserTypes: string[]
}>()

const emit = defineEmits<{
  (e: 'add', memberId: number): void
  (e: 'remove', memberId: number): void
}>()

/** Named the same way the menu names them, so somebody without a name is still somebody on the list. */
const current = computed(() => props.sortedGroupMembers.map(fromMember))

const {picked, take} = useMemberPick(memberId => emit('add', memberId))

/** The panel is one panel, but a list of stations is not called a list of members. */
const capabilities = useGroupsCapabilities()
const holdsStations = computed(() => capabilities.holds === 'stations')
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
    <MutedText tag="div" size="sm" class="py-2" v-if="current.length === 0">
      {{ words.none }}
    </MutedText>
    <div class="space-y-1">
      <div v-for="member in current" :key="member.value"
           class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
        <div>
          <MemberName :identity="identityOf(member)" class="text-sm font-medium"/>
          <MutedText v-if="member.email" class="ml-2">{{ member.email }}</MutedText>
        </div>
        <IconButton :icon="['fas', 'xmark']" :label="words.remove" class="text-error hover:text-error/80 text-sm" @click="emit('remove', Number(member.value))"/>
      </div>
    </div>
  </div>

  <div class="space-y-1">
    <FieldLabel class="text-(--text-muted)">{{ words.add }}</FieldLabel>
    <MutedText tag="div" size="sm" class="py-2" v-if="availableMembers.length === 0">
      {{ words.allAdded }}
    </MutedText>
    <GroupStationPicker
        v-else-if="holdsStations"
        :stations="availableMembers"
        @add="id => emit('add', id)"
    />
    <MemberSelectInput
        v-else
        v-model="picked"
        :members="availableMembers"
        :user-types="offeredUserTypes"
        :placeholder="words.add"
        @change="take"
    />
  </div>
</template>
