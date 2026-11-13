/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {configOf, spanForWidth} from '@/components/profilefields/fieldLayout'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import QuestionValueDisplay from '@/components/display/QuestionValueDisplay.vue'
import PerDateFieldValue from './PerDateFieldValue.vue'
import type {AppointmentField} from '@/api/generated/schema'
import SelfRegistrationToggle from '../eventshared/SelfRegistrationToggle.vue'
import type {MemberLike} from '@/components/input/select/memberOption'
import {events} from '@/api'

const props = defineProps<{
  eventId: number
  fields: AppointmentField[]
  allMembers: MemberLike[]
  currentMemberId: number
  /** The attendance template, which takes a row of its own so the fields below line up beneath it. */
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

</script>

<template>
  <div class="grid grid-cols-6 gap-4">
    <div v-if="canEditEvent" class="col-span-6">
      <DetailLabel>{{ t('events.template') }}</DetailLabel>
      <p class="text-sm">{{ templateName }}</p>
    </div>
    <div v-for="field in fields" :key="field.id" :class="spanForWidth(configOf(field.config).width)">
      <DetailLabel>{{ field.name }}</DetailLabel>
      <p class="text-sm">
        <QuestionValueDisplay :field-type="field.fieldType" :member-names="memberNames" :value="field.value"/>
      </p>
      <SelfRegistrationToggle
          :field="field"
          :current-member-id="currentMemberId"
          :toggle="() => events.toggleFieldSelfRegistration(eventId, field.id, effectiveDate)"
          :enter-label="field.readerInWholeGroup ? t('eventGroups.backOnThisDate') : undefined"
          :leave-label="field.readerInWholeGroup ? t('eventGroups.skipThisDate') : undefined"
          @updated="updated => emit('field-updated', updated)"
      />
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
