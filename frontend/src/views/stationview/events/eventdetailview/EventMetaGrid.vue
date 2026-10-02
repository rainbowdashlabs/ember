/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {AxiosError} from 'axios'
import {configOf, spanForWidth} from '@/components/profilefields/fieldLayout'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import QuestionValueDisplay from '@/components/display/QuestionValueDisplay.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PerDateFieldValue from './PerDateFieldValue.vue'
import {namesMembers, namesSeveralMembers} from '@/api/fieldTypes'
import type {AppointmentField} from '@/api/generated/schema'
import type {MemberLike} from '@/components/input/select/memberOption'
import {events} from '@/api'
import {memberIdsOf} from '@/util/questions'
import {showToast} from '@/util/toast'

const props = defineProps<{
  eventId: number
  fields: AppointmentField[]
  allMembers: MemberLike[]
  currentMemberId: number
  startFormatted: string
  endFormatted: string
  templateName: string
  /** Which sheet the attendance is taken on is a setting, so only whoever sets it is shown it. */
  canEditEvent: boolean
  /** The occurrence on screen, which is the one a question answered per date is answered for. */
  effectiveDate?: string | null
}>()

const emit = defineEmits<{
  (e: 'field-updated', field: AppointmentField): void
}>()

const {t} = useI18n()

/** The station's members by id, under the name a reader knows them by. */
const memberNames = computed(() =>
    new Map(props.allMembers.map(m => [m.id, m.name ?? m.email ?? `#${m.id}`] as const)))

function offersSelfRegistration(field: AppointmentField): boolean {
  return namesMembers(field.fieldType) && field.config.selfRegistration
}

type SelfRegState = 'CAN_REGISTER' | 'CAN_REMOVE' | 'OCCUPIED'

function selfRegState(field: AppointmentField): SelfRegState {
  const ids = memberIdsOf(field.value).map(Number)
  if (ids.includes(props.currentMemberId)) return 'CAN_REMOVE'
  if (!namesSeveralMembers(field.fieldType) && ids.length > 0) return 'OCCUPIED'
  return 'CAN_REGISTER'
}

const submitting = ref<Set<number>>(new Set())

async function toggle(field: AppointmentField) {
  if (submitting.value.has(field.id)) return
  submitting.value.add(field.id)
  try {
    const updated = await events.toggleFieldSelfRegistration(props.eventId, field.id, props.effectiveDate)
    emit('field-updated', updated)
  } catch (e) {
    const status = e instanceof AxiosError ? e.response?.status : undefined
    if (status === 409) {
      showToast(t('eventFields.selfRegisterConflict'), 'error')
    } else if (status === 403) {
      showToast(t('eventFields.selfRegisterForbidden'), 'error')
    } else {
      showToast(t('eventFields.selfRegisterFailed'), 'error')
    }
  } finally {
    submitting.value.delete(field.id)
  }
}
</script>

<template>
  <div class="grid grid-cols-6 gap-4">
    <div class="col-span-3">
      <DetailLabel>{{ t('events.startTime') }}</DetailLabel>
      <p class="text-sm" data-testid="event-start">{{ startFormatted }}</p>
    </div>
    <div class="col-span-3">
      <DetailLabel>{{ t('events.endTime') }}</DetailLabel>
      <p class="text-sm" data-testid="event-end">{{ endFormatted }}</p>
    </div>
    <div v-if="canEditEvent" class="col-span-6 sm:col-span-3">
      <DetailLabel>{{ t('events.template') }}</DetailLabel>
      <p class="text-sm">{{ templateName }}</p>
    </div>
    <div v-for="field in fields" :key="field.id" :class="spanForWidth(configOf(field.config).width)">
      <DetailLabel>{{ field.name }}</DetailLabel>
      <p class="text-sm">
        <QuestionValueDisplay :field-type="field.fieldType" :member-names="memberNames" :value="field.value"/>
      </p>
      <div v-if="offersSelfRegistration(field)" class="mt-1">
        <PrimaryButton
            v-if="selfRegState(field) === 'CAN_REGISTER'"
            :disabled="submitting.has(field.id)"
            @click="toggle(field)"
        >
          {{ t('eventFields.selfRegister') }}
        </PrimaryButton>
        <SecondaryButton
            v-else-if="selfRegState(field) === 'CAN_REMOVE'"
            :disabled="submitting.has(field.id)"
            @click="toggle(field)"
        >
          {{ t('eventFields.removeSelf') }}
        </SecondaryButton>
        <SecondaryButton
            v-else
            :disabled="true"
        >
          {{ t('eventFields.slotTaken') }}
        </SecondaryButton>
      </div>
      <PerDateFieldValue
          v-if="canEditEvent && effectiveDate && field.config.perDate"
          :event-id="eventId"
          :field="field"
          :date="effectiveDate"
          :all-members="allMembers"
          @saved="emit('field-updated', $event)"
      />
    </div>
  </div>
</template>
