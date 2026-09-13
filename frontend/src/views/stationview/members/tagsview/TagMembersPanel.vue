/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import IconButton from '@/components/button/IconButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {useMemberPick} from '@/composables/useMemberPick'
import {fromMember, identityOf, type MemberOption} from '@/components/input/select/memberOption'
import type {UserTag} from '@/api/types'
import type {AssignableMember} from '@/composables/useGroupsConfig'

const {t} = useI18n()

const props = defineProps<{
  selectedTag: UserTag
  tagLoading: boolean
  tagMembers: AssignableMember[]
  availableMembers: MemberOption[]
  /** The kinds of member still on offer, for the filter beside the search. */
  offeredUserTypes: string[]
}>()

const emit = defineEmits<{
  (e: 'add-member', memberId: number): void
  (e: 'remove-member', memberId: number): void
}>()

/** Named the same way the menu names them, so somebody without a name is still somebody on the list. */
const current = computed(() => props.tagMembers.map(fromMember))

const {picked, take} = useMemberPick(memberId => emit('add-member', memberId))
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ selectedTag.name }}</SubHeader>

    <Spinner v-if="tagLoading" size="md"/>

    <template v-if="!tagLoading">
      <div class="space-y-1">
        <FieldLabel class="text-(--text-muted)">{{ t('userTags.currentMembers') }}</FieldLabel>
        <MutedText tag="div" size="sm" class="py-2" v-if="current.length === 0">
          {{ t('userTags.noMembers') }}
        </MutedText>
        <div class="space-y-1">
          <div v-for="member in current" :key="member.value"
               class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
            <div>
              <MemberName :identity="identityOf(member)" class="text-sm font-medium"/>
              <MutedText v-if="member.email" tag="div" class="ml-7">{{ member.email }}</MutedText>
            </div>
            <IconButton :icon="['fas', 'xmark']" :label="t('userTags.removeMember')" class="text-error hover:text-error/80 text-sm" @click="emit('remove-member', Number(member.value))"/>
          </div>
        </div>
      </div>

      <div class="space-y-1">
        <FieldLabel class="text-(--text-muted)">{{ t('userTags.addMembers') }}</FieldLabel>
        <MutedText tag="div" size="sm" class="py-2" v-if="availableMembers.length === 0">
          {{ t('userTags.allAdded') }}
        </MutedText>
        <MemberSelectInput
            v-else
            v-model="picked"
            :members="availableMembers"
            :user-types="offeredUserTypes"
            :placeholder="t('userTags.addMembers')"
            @change="take"
        />
      </div>
    </template>
  </div>
</template>
