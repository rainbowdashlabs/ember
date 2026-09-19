/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import EditButton from '@/components/button/EditButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import RegistrationFieldAnswers from '../RegistrationFieldAnswers.vue'
import type {EventRegistrationEntry, EventRegistrationField} from '@/api/events'

/** Who signed up on the ranking, with what they answered and whether an answer is still missing. */
defineProps<{
  registration: EventRegistrationEntry
  fields: EventRegistrationField[]
  canEditAnswers: boolean
}>()

const emit = defineEmits<{
  editAnswers: [registrationId: number]
}>()

const {t} = useI18n()
</script>

<template>
  <div>
    <div class="flex items-center gap-2 flex-wrap">
      <MemberName :identity="registration.memberIdentity ?? null"/>
      <ErrorBadge v-if="registration.answersMissing" :data-testid="`answer-missing-${registration.id}`">
        {{ t('eventDetail.answerMissing') }}
      </ErrorBadge>
    </div>
    <div class="flex items-center gap-2 flex-wrap">
      <RegistrationFieldAnswers :fields="fields" :values="registration.fields" class="mt-1"/>
      <EditButton
          v-if="canEditAnswers"
          :data-testid="`edit-answers-${registration.id}`"
          @click.stop="emit('editAnswers', registration.id)"
      />
    </div>
  </div>
</template>
