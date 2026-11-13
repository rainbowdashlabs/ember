/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import RowLink from '@/components/navigation/RowLink.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import type {CourseSummary} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import {formatDateTime} from '@/util/format'

/** One course in the list: its name, how many parts and places it has, and when sign-ups close. */
defineProps<{
  course: CourseSummary
}>()

const {t} = useI18n()
const {stationTimezone} = useSession()
</script>

<template>
  <RowLink :to="{name: 'event-course', params: {courseId: course.id}}">
    <NeutralContainer class="flex flex-wrap items-center justify-between gap-2 hover:bg-(--bg-accent)" data-testid="course-row">
      <div class="min-w-0">
        <p class="font-medium truncate">{{ course.name }}</p>
        <MutedText tag="p" size="sm">
          {{ t('courses.partCount', {count: course.partCount}) }}
          <template v-if="course.registrationDeadline"> · {{ t('courses.deadline', {date: formatDateTime(course.registrationDeadline, stationTimezone)}) }}</template>
        </MutedText>
      </div>
      <div class="flex items-center gap-2 shrink-0">
        <ErrorBadge v-if="course.cancelled">{{ t('courses.cancelled') }}</ErrorBadge>
        <MutedText tag="span" size="sm">
          {{ course.registrationLimit != null
            ? t('courses.places', {holding: course.holding, limit: course.registrationLimit})
            : t('courses.placesOpen', {holding: course.holding}) }}
        </MutedText>
      </div>
    </NeutralContainer>
  </RowLink>
</template>
