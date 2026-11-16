/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import AppIcon from '@/components/display/AppIcon.vue'
import MarkToggle from '@/components/input/questionsettings/MarkToggle.vue'

/**
 * The marks on an appointment field's line: whether it shows in the overview and to readers outside,
 * and whether it fills in the attendance sheet.
 *
 * <p>Overview and public switch with a click, since those are what gets changed most after a field
 * is set up; the tie to the attendance sheet is only shown here and set in the field's settings.
 */
const overview = defineModel<boolean>('overview', {required: true})
const isPublic = defineModel<boolean>('isPublic', {required: true})

defineProps<{
  /** The attendance field the answer fills in, if any. */
  attendanceName?: string
}>()

const {t} = useI18n()
</script>

<template>
  <MarkToggle v-model="overview" :icon="['fas', 'eye']" :label="t('eventFields.overview')"
              data-testid="event-field-row-overview"/>
  <MarkToggle v-model="isPublic" :icon="['fas', 'globe']" :label="t('eventFields.public')"
              data-testid="event-field-row-public"/>
  <span
      v-if="attendanceName"
      :title="`${t('eventFields.attendanceLink')}: ${attendanceName}`"
      class="inline-flex p-2 text-primary"
      data-testid="event-field-row-attendance"
  >
    <AppIcon :icon="['fas', 'link']" class="h-4 w-4"/>
    <span class="sr-only">{{ t('eventFields.attendanceLink') }}: {{ attendanceName }}</span>
  </span>
</template>
