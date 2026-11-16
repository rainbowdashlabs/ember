/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {AxiosError} from 'axios'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {namesMembers, namesSeveralMembers} from '@/api/fieldTypes'
import {SelfRegistration, type AppointmentField} from '@/api/generated/schema'
import {memberIdsOf} from '@/util/questions'
import {showToast} from '@/util/toast'

/**
 * What the reader can do about a member question that takes self-registration: put themselves in, take
 * themselves out, or see why they cannot. The same for an appointment's own field and for a field of an
 * event group, for the whole group or one of its appointments; only where the toggle goes differs.
 *
 * Stepping out is always offered to whoever stands in it, because the server never refuses it.
 * Stepping in follows the state the server worked out for this reader and is offered only where it
 * says open, so a question narrowed to people the reader is not among, or one that reached the screen
 * without a state, offers nothing rather than a button that is then refused.
 */
const props = defineProps<{
  field: AppointmentField
  currentMemberId: number
  /** Puts the reader in or takes them out, answering with the field as it stands afterwards. */
  toggle: () => Promise<AppointmentField>
  /** What putting oneself in is called here, where the plain wording would mislead. */
  enterLabel?: string
  /** What taking oneself out is called here, where the plain wording would mislead. */
  leaveLabel?: string
}>()

const emit = defineEmits<{
  updated: [field: AppointmentField]
}>()

const {t} = useI18n()

type SelfRegState = 'CAN_REGISTER' | 'CAN_REMOVE' | 'OCCUPIED' | 'CLOSED' | 'NOT_OFFERED'

const state = computed<SelfRegState>(() => {
  const field = props.field
  if (!namesMembers(field.fieldType) || !field.config.selfRegistration) return 'NOT_OFFERED'
  const ids = memberIdsOf(field.value).map(Number)
  if (ids.includes(props.currentMemberId)) return 'CAN_REMOVE'
  if (field.selfRegistrationState === SelfRegistration.CLOSED) return 'CLOSED'
  if (field.selfRegistrationState !== SelfRegistration.OPEN) return 'NOT_OFFERED'
  if (!namesSeveralMembers(field.fieldType) && ids.length > 0) return 'OCCUPIED'
  return 'CAN_REGISTER'
})

const submitting = ref(false)

async function run() {
  if (submitting.value) return
  submitting.value = true
  try {
    emit('updated', await props.toggle())
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
    submitting.value = false
  }
}
</script>

<template>
  <div v-if="state !== 'NOT_OFFERED'" class="mt-1">
    <PrimaryButton v-if="state === 'CAN_REGISTER'" :disabled="submitting" data-testid="self-registration-enter" @click="run">
      {{ enterLabel ?? t('eventFields.selfRegister') }}
    </PrimaryButton>
    <SecondaryButton v-else-if="state === 'CAN_REMOVE'" :disabled="submitting" data-testid="self-registration-leave" @click="run">
      {{ leaveLabel ?? t('eventFields.removeSelf') }}
    </SecondaryButton>
    <SecondaryButton v-else :disabled="true">
      {{ state === 'CLOSED' ? t('eventFields.selfRegisterClosed') : t('eventFields.slotTaken') }}
    </SecondaryButton>
  </div>
</template>
