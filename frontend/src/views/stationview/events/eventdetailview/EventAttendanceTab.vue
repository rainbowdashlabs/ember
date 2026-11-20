/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

/**
 * Taking attendance for the date on screen, in a tab of its own rather than only in the actions menu:
 * it opens the attendance list already kept for this date, or starts one.
 */
defineProps<{
  /** The attendance list already kept for this date, or none yet. */
  attendanceSessionId: number | null
}>()

const emit = defineEmits<{
  attendance: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="event-attendance-tab">
    <SectionHeader>{{ t('eventDetail.tabAttendance') }}</SectionHeader>
    <MutedText tag="p" size="sm">
      {{ attendanceSessionId ? t('eventDetail.attendanceTaken') : t('eventDetail.attendanceNotTaken') }}
    </MutedText>
    <PrimaryButton :icon="['fas', 'clipboard-user']" data-testid="event-attendance-tab-open" @click="emit('attendance')">
      {{ attendanceSessionId ? t('events.openAttendance') : t('events.takeAttendance') }}
    </PrimaryButton>
  </NeutralContainer>
</template>
