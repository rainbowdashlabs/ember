/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TimeInput from '@/components/input/datetime/TimeInput.vue'
import OccurrenceSearchPicker from '@/components/input/search/OccurrenceSearchPicker.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type {InvitationDateDraft} from './invitationDates'

/**
 * One date an invitation offers: the occurrence of an appointment and the time the person is asked
 * to be there on it. The first row carries the test ids the single-date dialog always had.
 */
const draft = defineModel<InvitationDateDraft>({required: true})

const props = defineProps<{index: number, removable: boolean}>()

const emit = defineEmits<{remove: []}>()

const {t} = useI18n()

const suffix = computed(() => props.index === 0 ? '' : `-${props.index}`)
</script>

<template>
  <div class="space-y-2" :data-testid="`waitlist-invite-date${suffix}`">
    <div class="flex items-end gap-2">
      <div class="flex-1 space-y-1">
        <FieldLabel>{{ t('waitingList.inviteDateNumber', {number: props.index + 1}) }}</FieldLabel>
        <OccurrenceSearchPicker
            :model-value="draft.occurrence"
            :placeholder="t('waitingList.invitePickAppointment')"
            :empty-label="t('waitingList.inviteNoAppointments')"
            :testid="`waitlist-invite-occurrence${suffix}`"
            :data-autofocus="props.index === 0 ? '' : undefined"
            @update:model-value="draft = {...draft, occurrence: $event}"
        />
      </div>
      <DeleteButton
          v-if="props.removable"
          :data-testid="`waitlist-invite-remove${suffix}`"
          @click="emit('remove')"
      />
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.inviteArrival') }}</FieldLabel>
      <TimeInput :model-value="draft.arrivalTime" @update:model-value="draft = {...draft, arrivalTime: $event ?? ''}" />
    </div>
  </div>
</template>
