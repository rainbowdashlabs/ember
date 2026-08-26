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
import EventFieldValue from '../eventshared/EventFieldValue.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {EventFieldTypes, type EventField} from '@/api/events'
import type {StationMember} from '@/api/types'
import {events} from '@/api'
import {showToast} from '@/util/toast'

const props = defineProps<{
  eventId: number
  fields: EventField[]
  allMembers: StationMember[]
  currentMemberId: number
  startFormatted: string
  endFormatted: string
  categoryName: string
  templateName: string
  canManageEvents: boolean
}>()

const emit = defineEmits<{
  (e: 'field-updated', field: EventField): void
}>()

const {t} = useI18n()

const memberById = computed(() => {
  const map = new Map<number, StationMember>()
  for (const m of props.allMembers) map.set(m.id, m)
  return map
})

const MEMBER_FIELDS: string[] = [
  EventFieldTypes.MEMBER,
  EventFieldTypes.MEMBER_LIST,
  EventFieldTypes.MEMBER_OF_GROUP,
  EventFieldTypes.MEMBER_LIST_OF_GROUP,
  EventFieldTypes.MEMBER_OF_TYPE,
  EventFieldTypes.MEMBER_LIST_OF_TYPE,
  EventFieldTypes.MEMBER_OF_TAG,
  EventFieldTypes.MEMBER_LIST_OF_TAG,
]

const LIST_FIELDS: string[] = [
  EventFieldTypes.MEMBER_LIST,
  EventFieldTypes.MEMBER_LIST_OF_GROUP,
  EventFieldTypes.MEMBER_LIST_OF_TYPE,
  EventFieldTypes.MEMBER_LIST_OF_TAG,
]

function isMemberField(field: EventField): boolean {
  return MEMBER_FIELDS.includes(field.fieldType ?? '')
}

function isListField(field: EventField): boolean {
  return LIST_FIELDS.includes(field.fieldType ?? '')
}

function selfRegistrationEnabled(field: EventField): boolean {
  const cfg = (field.config ?? {}) as { selfRegistration?: boolean }
  return cfg.selfRegistration === true
}

function memberIdsOf(field: EventField): number[] {
  const raw = field.value
  if (!raw) return []
  const trimmed = raw.trim()
  try {
    const parsed = JSON.parse(trimmed)
    if (Array.isArray(parsed)) return parsed.map(Number).filter(n => Number.isFinite(n))
    if (typeof parsed === 'number') return [parsed]
  } catch { /* ignore */ }
  const n = Number(trimmed)
  return Number.isFinite(n) && trimmed !== '' ? [n] : []
}

function memberNamesOf(field: EventField): string {
  const ids = memberIdsOf(field)
  if (!ids.length) return '–'
  return ids
      .map(id => {
        const m = memberById.value.get(id)
        return m?.name ?? m?.email ?? `#${id}`
      })
      .join(', ')
}

type SelfRegState = 'CAN_REGISTER' | 'CAN_REMOVE' | 'OCCUPIED'

function selfRegState(field: EventField): SelfRegState {
  const ids = memberIdsOf(field)
  if (ids.includes(props.currentMemberId)) return 'CAN_REMOVE'
  if (!isListField(field) && ids.length > 0) return 'OCCUPIED'
  return 'CAN_REGISTER'
}

const submitting = ref<Set<number>>(new Set())

async function toggle(field: EventField) {
  if (submitting.value.has(field.id)) return
  submitting.value.add(field.id)
  try {
    const updated = await events.toggleFieldSelfRegistration(props.eventId, field.id)
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
    <div class="col-span-6 sm:col-span-3">
      <DetailLabel>{{ t('events.category') }}</DetailLabel>
      <p class="text-sm">{{ categoryName }}</p>
    </div>
    <div class="col-span-6 sm:col-span-3">
      <DetailLabel>{{ t('events.startTime') }}</DetailLabel>
      <p class="text-sm">{{ startFormatted }}</p>
    </div>
    <div class="col-span-6 sm:col-span-3">
      <DetailLabel>{{ t('events.endTime') }}</DetailLabel>
      <p class="text-sm">{{ endFormatted }}</p>
    </div>
    <div v-if="canManageEvents" class="col-span-6 sm:col-span-3">
      <DetailLabel>{{ t('events.template') }}</DetailLabel>
      <p class="text-sm">{{ templateName }}</p>
    </div>
    <div v-for="field in fields" :key="field.id" :class="spanForWidth(configOf(field.config).width)">
      <DetailLabel>{{ field.name }}</DetailLabel>
      <p v-if="isMemberField(field)" class="text-sm">{{ memberNamesOf(field) }}</p>
      <p v-else class="text-sm">
        <EventFieldValue :field-type="field.fieldType" :value="field.value"/>
      </p>
      <div v-if="isMemberField(field) && selfRegistrationEnabled(field)" class="mt-1">
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
    </div>
  </div>
</template>
