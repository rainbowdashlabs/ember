/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import RadioInput from '@/components/input/toggle/RadioInput.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import ChecklistOccurrencePicker from './ChecklistOccurrencePicker.vue'
import type {RestrictionSelection} from '@/components/input/restriction'
import type {ChecklistSourceRequest} from '@/api/checklists'
import type {MemberGroup, StationMember, UserTag} from '@/api/types'

/**
 * Chooses what a list is made of: a description of people, or one evening of an appointment.
 *
 * <p>Never both. An appointment already carries its own audience, and the people holding a place
 * on it are what came out of that, so a group condition on top would narrow a set that has been
 * narrowed once already. Picking one side therefore puts the other away rather than adding to it.
 */
const follows = defineModel<'FILTER' | 'EVENT'>('follows', {required: true})
const restriction = defineModel<RestrictionSelection>('restriction', {required: true})
const occurrence = defineModel<ChecklistSourceRequest | null>('occurrence', {required: true})

defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
  members: StationMember[]
  /** What the chip should read for an evening this list already follows. */
  selectedOccurrenceLabel?: string | null
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <FieldLabel>{{ t('checklist.memberSet') }}</FieldLabel>

    <label class="flex items-start gap-2 cursor-pointer">
      <RadioInput v-model="follows" value="FILTER" class="mt-1" data-testid="checklist-follows-filter"/>
      <span>
        <span class="text-sm font-medium">{{ t('checklist.followsFilter') }}</span>
        <MutedText tag="span" size="sm" class="block">{{ t('checklist.followsFilterHelp') }}</MutedText>
      </span>
    </label>

    <div v-if="follows === 'FILTER'" class="pl-6">
      <RestrictionPicker
          v-model="restriction"
          :groups="groups"
          :tags="tags"
          :members="members"
          :show-members="true"
          :show-mode="true"
      />
    </div>

    <label class="flex items-start gap-2 cursor-pointer">
      <RadioInput v-model="follows" value="EVENT" class="mt-1" data-testid="checklist-follows-event"/>
      <span>
        <span class="text-sm font-medium">{{ t('checklist.followsEvent') }}</span>
        <MutedText tag="span" size="sm" class="block">{{ t('checklist.followsEventHelp') }}</MutedText>
      </span>
    </label>

    <div v-if="follows === 'EVENT'" class="pl-6 space-y-2">
      <ChecklistOccurrencePicker v-model="occurrence" :selected-display="selectedOccurrenceLabel"/>
      <MutedText tag="p" size="sm">{{ t('checklist.followsEventGuests') }}</MutedText>
      <Alert variant="info">{{ t('checklist.followsEventRefresh') }}</Alert>
    </div>
  </div>
</template>
