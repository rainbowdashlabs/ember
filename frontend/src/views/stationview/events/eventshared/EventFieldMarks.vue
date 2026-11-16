/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import {fieldTypeLabel} from '@/api/fieldTypes'

/**
 * The end of a field's line: its type, whether it shows in the overview and to readers outside,
 * whether it fills in the attendance sheet, and the buttons to open its settings or take it away.
 *
 * <p>Overview and public switch with a click, since those are what gets changed most after a field
 * is set up; the tie to the attendance sheet is only shown here and set in the field's settings.
 */
defineProps<{
  fieldType: string
  overview: boolean
  isPublic: boolean
  /** The attendance field the answer fills in, if any. */
  attendanceName?: string
}>()

const emit = defineEmits<{
  'update:overview': [overview: boolean]
  'update:isPublic': [isPublic: boolean]
  open: []
  remove: []
}>()

const {t} = useI18n()

const STATE_ON = 'text-primary'
const STATE_OFF = 'text-(--text-muted) opacity-50'
</script>

<template>
  <div class="flex shrink-0 items-center gap-1">
    <SecondaryBadge data-testid="event-field-row-type">{{ fieldTypeLabel(t, fieldType) }}</SecondaryBadge>
    <IconButton
        :icon="['fas', 'eye']"
        :label="t('eventFields.overview')"
        :aria-pressed="overview"
        :class="overview ? STATE_ON : STATE_OFF"
        data-testid="event-field-row-overview"
        @click="emit('update:overview', !overview)"
    />
    <IconButton
        :icon="['fas', 'globe']"
        :label="t('eventFields.public')"
        :aria-pressed="isPublic"
        :class="isPublic ? STATE_ON : STATE_OFF"
        data-testid="event-field-row-public"
        @click="emit('update:isPublic', !isPublic)"
    />
    <span
        v-if="attendanceName"
        :title="`${t('eventFields.attendanceLink')}: ${attendanceName}`"
        class="inline-flex p-2 text-primary"
        data-testid="event-field-row-attendance"
    >
      <AppIcon :icon="['fas', 'link']" class="h-4 w-4"/>
      <span class="sr-only">{{ t('eventFields.attendanceLink') }}: {{ attendanceName }}</span>
    </span>
    <IconButton
        :icon="['fas', 'chevron-down']"
        :label="t('eventFields.openSettings')"
        data-testid="event-field-row-open"
        @click="emit('open')"
    />
    <IconButton
        :icon="['fas', 'trash']"
        :label="t('common.delete')"
        data-testid="event-field-row-remove"
        @click="emit('remove')"
    />
  </div>
</template>
