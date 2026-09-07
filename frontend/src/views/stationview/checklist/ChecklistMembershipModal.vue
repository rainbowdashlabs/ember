/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ChecklistMembershipEditor from './checklistmodals/ChecklistMembershipEditor.vue'
import {toRestriction, type RestrictionSelection} from '@/components/input/restriction'
import type {
  ChecklistRestrictionDto,
  ChecklistSourceOccurrence,
  ChecklistSourceRequest,
} from '@/api/checklists'
import type {MemberGroup, StationMember, UserTag} from '@/api/types'
import {formatDate} from '@/util/format'

/**
 * Changes what an existing list is made of, which nothing could do before.
 *
 * <p>Only the create dialog ever set this, so a list built from a fixed set of names could never
 * become one that follows an evening, however clearly that was what somebody wanted. Saving here
 * changes what the list will resolve next; it adds and removes nobody on its own, because bringing
 * people in is what the refresh button is for.
 */
const visible = defineModel<boolean>({required: true})

const props = defineProps<{
  initialRestriction: ChecklistRestrictionDto
  initialSource?: ChecklistSourceOccurrence | null
  groups: MemberGroup[]
  tags: UserTag[]
  members: StationMember[]
  saving: boolean
  error?: string
}>()

const emit = defineEmits<{
  (e: 'submit', payload: {restriction?: ChecklistRestrictionDto; source?: ChecklistSourceRequest}): void
}>()

const {t} = useI18n()

const follows = ref<'FILTER' | 'EVENT'>('FILTER')
const restriction = ref<RestrictionSelection>(toRestriction(props.initialRestriction))
const occurrence = ref<ChecklistSourceRequest | null>(null)

const initialLabel = computed(() => {
  const source = props.initialSource
  if (!source) return null
  const name = source.eventName ?? ''
  return `${name} ${t('checklist.occurrenceOn', {date: formatDate(source.eventDate)})}`.trim()
})

watch(visible, opened => {
  if (!opened) return
  restriction.value = toRestriction(props.initialRestriction)
  occurrence.value = props.initialSource
      ? {eventId: props.initialSource.eventId, date: props.initialSource.eventDate}
      : null
  follows.value = props.initialSource ? 'EVENT' : 'FILTER'
}, {immediate: true})

const incomplete = computed(() => follows.value === 'EVENT' && occurrence.value === null)

function submit() {
  if (incomplete.value) return
  if (follows.value === 'EVENT' && occurrence.value) {
    emit('submit', {source: occurrence.value})
    return
  }
  emit('submit', {
    restriction: {
      userTypes: restriction.value.userTypes,
      groupIds: restriction.value.groupIds,
      tagIds: restriction.value.tagIds,
      memberIds: restriction.value.memberIds,
      mode: restriction.value.mode,
    },
  })
}
</script>

<template>
  <Modal v-model="visible" size="xl">
    <div class="space-y-4">
      <SubHeader>{{ t('checklist.membershipTitle') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('checklist.membershipHelp') }}</MutedText>

      <ChecklistMembershipEditor
          v-model:follows="follows"
          v-model:restriction="restriction"
          v-model:occurrence="occurrence"
          :groups="groups"
          :tags="tags"
          :members="members"
          :selected-occurrence-label="initialLabel"
      />

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <div class="flex justify-end gap-2 pt-2">
        <SecondaryButton data-cancel @click="visible = false">{{ t('checklist.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || incomplete" data-testid="checklist-membership-save" @click="submit">
          {{ t('checklist.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
