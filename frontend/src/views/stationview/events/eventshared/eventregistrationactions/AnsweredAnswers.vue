/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts" generic="K extends string | number, U">
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import RegistrationStatusBadge from '../RegistrationStatusBadge.vue'
import {isRefusal, type GivenAnswer} from '@/util/eventAnswers'
import type {RegistrationStatusName} from '@/api/events'

/**
 * What each person who has answered said, and the way to take it back.
 *
 * <p>One row per person rather than all of them across one wrapping line: somebody answering for
 * three children needs to see whose answer is whose.
 */
const props = defineProps<{
  rows: GivenAnswer<K, U>[]
  showNames: boolean
  /** Whether the appointment has to be signed up for, which decides what deleting a refusal means. */
  requiresRegistration: boolean
  registering: boolean
}>()

const emit = defineEmits<{
  undo: [reference: U]
}>()

const {t} = useI18n()

/**
 * What taking one answer back is called, which follows the answer rather than the appointment.
 *
 * <p>Undoing is a deletion in both directions, never the opposite answer written down, so the word
 * has to say what disappears. A place given up is being signed off. A refusal taken back leaves no
 * answer at all, and what that means depends on the appointment: where everybody is expected it puts
 * the person back among them, and where a place has to be asked for it simply clears the refusal.
 * Reading this off the appointment alone once put "sign off" behind somebody who had already
 * refused.
 */
function undoLabel(status: RegistrationStatusName): string {
  if (!isRefusal(status)) return t('eventsUpcoming.unregister')
  return props.requiresRegistration
      ? t('eventsUpcoming.undoDecline')
      : t('eventsUpcoming.register')
}
</script>

<template>
  <div v-for="row in rows" :key="`reg-${row.key}`" class="flex flex-wrap items-center gap-2">
    <span v-if="showNames" class="min-w-28 truncate text-xs text-(--text-muted)">{{ row.name }}</span>
    <RegistrationStatusBadge :status="row.status"/>

    <SecondaryButton
        :disabled="registering"
        :icon="['fas', 'rotate-left']"
        :data-testid="`undo-answer-${row.key}`"
        compact
        class="text-sm"
        @click.stop="emit('undo', row.undo)"
    >
      {{ undoLabel(row.status) }}
    </SecondaryButton>

    <span v-if="row.createdByName" class="text-xs text-(--text-muted) italic">
      {{ t('common.createdBy', {name: row.createdByName}) }}
    </span>
  </div>
</template>
