/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import RegistrationFieldsModal from '../eventshared/RegistrationFieldsModal.vue'
import type {EventGroupSignUp, EventRegistrationField, EventRegistrationFieldValue} from '@/api/generated/schema'
import {eventGroups} from '@/api'
import {describeFailure, type Failure} from '@/util/failure'

/** Opens the answers a standing event group sign-up gave, to change them. */
const props = defineProps<{
  eventGroupId: number
  entry: EventGroupSignUp
  questions: EventRegistrationField[]
}>()

const emit = defineEmits<{
  saved: []
}>()

const {t} = useI18n()

const open = ref(false)
const busy = ref(false)
const failure = ref<Failure | null>(null)

async function save(values: EventRegistrationFieldValue[]) {
  busy.value = true
  failure.value = null
  try {
    await eventGroups.changeAnswers(props.eventGroupId, values, props.entry.memberId)
    open.value = false
    emit('saved')
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <SecondaryButton :icon="['fas', 'list-check']" data-testid="event-group-change-answers" @click="open = true">
    {{ t('eventGroups.changeAnswers') }}
  </SecondaryButton>
  <RegistrationFieldsModal
      v-model="open"
      :fields="questions"
      :values="entry.answers"
      :title="t('eventGroups.answersTitle', {name: entry.member.name ?? ''})"
      :confirm-label="t('common.save')"
      :busy="busy"
      :failure="failure"
      @confirm="save"
  />
</template>
