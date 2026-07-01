/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import IconButton from '@/components/button/IconButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type {StationMember, UserTag} from '@/api/types'

const {t} = useI18n()

defineProps<{
  selectedTag: UserTag
  tagLoading: boolean
  tagMembers: StationMember[]
  availableMembers: StationMember[]
}>()

const emit = defineEmits<{
  (e: 'add-member', member: StationMember): void
  (e: 'remove-member', member: StationMember): void
}>()
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ selectedTag.name }}</SubHeader>

    <Spinner v-if="tagLoading" size="md"/>

    <template v-if="!tagLoading">
      <div class="space-y-1">
        <FieldLabel class="text-(--text-muted)">{{ t('userTags.currentMembers') }}</FieldLabel>
        <MutedText tag="div" size="sm" class="py-2" v-if="tagMembers.length === 0">
          {{ t('userTags.noMembers') }}
        </MutedText>
        <div class="space-y-1">
          <div v-for="member in tagMembers" :key="member.id"
               class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
            <div>
              <MemberName :identity="member.identity" class="text-sm font-medium"/>
              <div v-if="member.name && member.email" class="text-xs text-(--text-muted) ml-7">{{ member.email }}</div>
            </div>
            <IconButton :icon="['fas', 'xmark']" :label="t('userTags.removeMember')" class="text-error hover:text-error/80 text-sm" @click="emit('remove-member', member)"/>
          </div>
        </div>
      </div>

      <div class="space-y-1">
        <FieldLabel class="text-(--text-muted)">{{ t('userTags.addMembers') }}</FieldLabel>
        <MutedText tag="div" size="sm" class="py-2" v-if="availableMembers.length === 0">
          {{ t('userTags.allAdded') }}
        </MutedText>
        <div class="space-y-1">
          <div
              v-for="member in availableMembers"
              :key="member.id"
              class="flex items-center justify-between rounded-lg px-3 py-2 hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent cursor-pointer transition-colors"
              @click="emit('add-member', member)"
          >
            <div>
              <MemberName :identity="member.identity" class="text-sm font-medium"/>
              <div v-if="member.name && member.email" class="text-xs text-(--text-muted) ml-7">{{ member.email }}</div>
            </div>
            <font-awesome-icon :icon="['fas', 'plus']" class="text-primary text-sm"/>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
