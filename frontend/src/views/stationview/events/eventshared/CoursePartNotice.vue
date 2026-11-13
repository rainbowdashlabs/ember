/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CoursePartBadge from './CoursePartBadge.vue'
import type {CoursePartSummary} from '@/api/generated/schema'

/**
 * Tells the reader of an appointment that it is a part of a course, and where the sign-up for it
 * and the settings that come with it live: on the course.
 */
defineProps<{
  course: CoursePartSummary
  /** What the notice says beside the course: the sign-up on the page, or the locked settings in the editor. */
  message: string
}>()

const {t} = useI18n()
</script>

<template>
  <Alert variant="info" data-testid="course-part-notice">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div class="space-y-1">
        <p>{{ t('courses.partOf', {course: course.courseName}) }} <CoursePartBadge :course="course"/></p>
        <p>{{ message }}</p>
      </div>
      <SecondaryButton :icon="['fas', 'graduation-cap']" :to="{name: 'event-course', params: {courseId: course.courseId}}">
        {{ t('courses.openCourse') }}
      </SecondaryButton>
    </div>
  </Alert>
</template>
