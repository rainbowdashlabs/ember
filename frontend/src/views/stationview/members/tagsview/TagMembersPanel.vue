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
import type {UserTag} from '@/api/generated/schema'
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
  'add-member': [memberId: number]
  'remove-member': [memberId: number]
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
        <MutedText v-if="current.length === 0" tag="div" size="sm" class="py-2">
          {{ t('userTags.noMembers') }}
        </MutedText>
        <div class="space-y-1">
          <div v-for="member in current" :key="member.value"
               class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
            <MemberName :identity="identityOf(member)" :detail="member.email" class="text-sm font-medium"/>
            <IconButton :icon="['fas', 'xmark']" :label="t('userTags.removeMember')" class="text-error hover:text-error/80 text-sm" @click="emit('remove-member', Number(member.value))"/>
          </div>
        </div>
      </div>

      <div class="space-y-1">
        <FieldLabel class="text-(--text-muted)">{{ t('userTags.addMembers') }}</FieldLabel>
        <MutedText v-if="availableMembers.length === 0" tag="div" size="sm" class="py-2">
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
