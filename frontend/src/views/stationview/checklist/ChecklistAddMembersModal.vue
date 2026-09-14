/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {fromMember, userTypesOf} from '@/components/input/select/memberOption'
import type {ChecklistEntryDto} from '@/api/checklists'
import type {StationMember} from '@/api/types'

const visible = defineModel<boolean>({required: true})

const props = defineProps<{
  adding: boolean
  members: StationMember[]
  aliveMemberIds: Set<number>
  removedEntries: ChecklistEntryDto[]
}>()

const emit = defineEmits<{
  (e: 'submit', memberIds: number[]): void
}>()

const {t} = useI18n()

const selected = ref<string[]>([])

const removedMemberIds = computed(() => new Set(props.removedEntries.map(e => e.memberId)))

/**
 * Everybody not on the list yet.
 *
 * <p>Somebody taken off it once is offered again and said to have been, because putting them back is
 * a normal thing to do and doing it blind is not.
 */
const candidates = computed(() => props.members
    .filter(member => !props.aliveMemberIds.has(member.id))
    .map(member => {
      const option = fromMember(member)
      return removedMemberIds.value.has(member.id)
          ? {...option, email: t('checklist.previouslyRemoved')}
          : option
    }))

const userTypes = computed(() => userTypesOf(candidates.value))

function reset() {
  selected.value = []
}

function cancel() {
  visible.value = false
  reset()
}

function submit() {
  if (selected.value.length === 0) return
  emit('submit', selected.value.map(Number))
}

watch(visible, (value, previous) => {
  if (previous && !value) reset()
})
</script>

<template>
  <Modal v-model="visible" size="lg">
    <div class="space-y-3">
      <SubHeader>{{ t('checklist.addMembersTitle') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('checklist.addMembersIntro') }}</MutedText>
      <MemberSelectInput
          v-model:selected="selected"
          multiple
          :members="candidates"
          :user-types="userTypes"
          :placeholder="t('checklist.searchPlaceholder')"
      />
      <ButtonRow pair align="end">
        <SecondaryButton @click="cancel">{{ t('checklist.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="adding || selected.length === 0" @click="submit">
          {{ t('checklist.save') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
