/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import type {AttendanceTemplateField} from '@/api/attendance'

/**
 * Taking the questions of the chosen attendance sheet into the appointment.
 *
 * <p>A question that fills in a field of the sheet had to be typed out again here: the same name,
 * the same type, the same list of options, and then tied to the sheet by hand from a dropdown. Every
 * one of those is a chance to get it wrong, and a question tied to nothing writes its answer
 * nowhere.
 *
 * <p>Taken from here it arrives already tied and already set up. One at a time where only a couple
 * of the sheet's fields are asked at the appointment, or the lot where the sheet is the point.
 *
 * <p>What has been taken is not offered again: a second copy would be a second question filling the
 * same field, and the sheet has only one answer to give it.
 */
const props = defineProps<{
  /** The fields of the chosen sheet, and of no other. */
  fields: AttendanceTemplateField[]
  /** The sheet fields the appointment's questions already fill in. */
  takenIds: number[]
}>()

const emit = defineEmits<{
  take: [field: AttendanceTemplateField]
  takeAll: []
}>()

const {t} = useI18n()

const taken = computed(() => new Set(props.takenIds))
const available = computed(() => props.fields.filter(field => !taken.value.has(field.id)))
</script>

<template>
  <NeutralContainer v-if="available.length > 0" class="space-y-2">
    <div class="flex items-center justify-between gap-2 flex-wrap">
      <MutedText size="sm">{{ t('eventFields.fromAttendance') }}</MutedText>
      <SecondaryButton
          v-if="available.length > 1"
          :icon="['fas', 'plus']"
          class="!py-1 !px-2 !text-xs"
          data-testid="take-all-attendance-fields"
          @click="emit('takeAll')"
      >
        {{ t('eventFields.takeAllFromAttendance') }}
      </SecondaryButton>
    </div>
    <div class="flex flex-wrap gap-2">
      <SecondaryButton
          v-for="field in available"
          :key="field.id"
          class="!py-1 !px-2 !text-xs"
          :data-testid="`take-attendance-field-${field.id}`"
          @click="emit('take', field)"
      >
        + {{ field.name }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
