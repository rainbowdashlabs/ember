/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {StationMember} from '@/api/types'

const props = defineProps<{
  currentMemberId: number
  managedMembers: StationMember[]
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'save', payload: { absentFrom: string; absentUntil: string; reason?: string; memberIds?: number[] }): void
  (e: 'cancel'): void
}>()

const {t} = useI18n()

const newAbsenceFrom = ref('')
const newAbsenceUntil = ref('')
const newAbsenceReason = ref('')
const selectedMemberIds = ref<Set<number>>(new Set([props.currentMemberId]))

const isAbsenceRangeInvalid = computed(() =>
    !!newAbsenceFrom.value
    && !!newAbsenceUntil.value
    && newAbsenceUntil.value < newAbsenceFrom.value,
)

function toggleMember(memberId: number) {
  const s = new Set(selectedMemberIds.value)
  if (s.has(memberId)) s.delete(memberId); else s.add(memberId)
  selectedMemberIds.value = s
}

function submit() {
  if (!newAbsenceFrom.value || !newAbsenceUntil.value) return
  if (isAbsenceRangeInvalid.value) return
  const memberIds = selectedMemberIds.value.size > 0 ? [...selectedMemberIds.value] : undefined
  emit('save', {
    absentFrom: newAbsenceFrom.value,
    absentUntil: newAbsenceUntil.value,
    reason: newAbsenceReason.value || undefined,
    memberIds,
  })
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="grid gap-4 sm:grid-cols-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('profile.absenceFrom') }}</FieldLabel>
        <DateInput v-model="newAbsenceFrom"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('profile.absenceUntil') }}</FieldLabel>
        <DateInput v-model="newAbsenceUntil"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('profile.absenceReason') }}</FieldLabel>
        <TextAreaInput v-model="newAbsenceReason" :placeholder="t('profile.absenceReasonPlaceholder')"/>
      </div>
    </div>

    <p v-if="isAbsenceRangeInvalid" class="text-sm text-error">
      {{ t('profile.absenceInvalidRange') }}
    </p>

    <div class="space-y-2">
      <FieldLabel>{{ t('profile.absenceFor') }}</FieldLabel>
      <div class="flex flex-wrap gap-2">
        <SelectionToggleButton
            :selected="selectedMemberIds.has(currentMemberId)"
            size="md"
            @toggle="toggleMember(currentMemberId)"
        >
          {{ t('profile.absenceMyself') }}
        </SelectionToggleButton>
        <SelectionToggleButton
            v-for="m in managedMembers"
            :key="m.id"
            :selected="selectedMemberIds.has(m.id)"
            size="md"
            @toggle="toggleMember(m.id)"
        >
          {{ m.name ?? m.email }}
        </SelectionToggleButton>
      </div>
    </div>

    <div class="flex gap-3">
      <PrimaryButton
          :disabled="saving || !newAbsenceFrom || !newAbsenceUntil || selectedMemberIds.size === 0 || isAbsenceRangeInvalid"
          @click="submit">
        {{ saving ? t('common.loading') : t('profile.absenceAdd') }}
      </PrimaryButton>
      <SecondaryButton @click="emit('cancel')">
        {{ t('common.cancel') }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
