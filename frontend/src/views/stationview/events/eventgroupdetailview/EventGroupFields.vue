/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import QuestionValueDisplay from '@/components/display/QuestionValueDisplay.vue'
import SelfRegistrationToggle from '../eventshared/SelfRegistrationToggle.vue'
import {namesMembers} from '@/api/fieldTypes'
import {eventGroups} from '@/api'
import type {AppointmentField} from '@/api/generated/schema'

/**
 * The fields an event group shows for all its appointments, the empty ones left out, except a member
 * field members may put themselves into: there the reader enters for the whole group, which every
 * appointment of it then shows. Where the page knows its reader and the group, the field offers that.
 */
const props = defineProps<{
  fields: AppointmentField[]
  /** The group the fields belong to, where the reader may put themselves in. */
  eventGroupId?: number
  /** The reader, where they may put themselves in. */
  currentMemberId?: number
  /** The station's members by id, under the name a reader knows them by. */
  memberNames?: Map<number, string>
  /** Called once the reader stepped in or out, since their places on the appointments changed too. */
  onChanged?: () => void
}>()

const {t} = useI18n()

const offersEntry = computed(() => props.eventGroupId != null && props.currentMemberId != null)

function takesEntry(field: AppointmentField): boolean {
  return offersEntry.value && namesMembers(field.fieldType) && !!field.config.selfRegistration
}

const shown = computed(() => props.fields.filter(field => (field.value ?? '').trim() !== '' || takesEntry(field)))
</script>

<template>
  <div v-if="shown.length > 0" class="grid grid-cols-1 sm:grid-cols-2 gap-3" data-testid="event-group-field-values">
    <div v-for="field in shown" :key="field.id">
      <DetailLabel>{{ field.name }}</DetailLabel>
      <p class="text-sm"><QuestionValueDisplay :field-type="field.fieldType" :member-names="memberNames" :value="field.value"/></p>
      <SelfRegistrationToggle
          v-if="takesEntry(field)"
          :field="field"
          :current-member-id="currentMemberId!"
          :enter-label="t('eventGroups.enterWholeGroup')"
          :toggle="() => eventGroups.toggleFieldSelfRegistration(eventGroupId!, field.id)"
          @updated="onChanged?.()"
      />
    </div>
  </div>
</template>
