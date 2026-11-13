/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import CourseListItem from './coursesview/CourseListItem.vue'
import type {CourseSummary} from '@/api/generated/schema'
import {StationPermission} from '@/api/generated/schema'
import {eventCourses} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

/**
 * The courses of the station: appointments that belong together and are signed up for once. Members
 * see the courses meant for them or for somebody they look after; whoever edits appointments sees
 * every course and creates new ones.
 */
const {t} = useI18n()
const {loaded, hasPermission} = useSession()

const courses = ref<CourseSummary[]>([])

const {loading, failure, reload} = useAsyncLoader(async () => {
  courses.value = await eventCourses.listCourses()
}, {autoLoad: loaded.value})

watch(loaded, ready => { if (ready) reload() })
</script>

<template>
  <ViewContent :title="t('pages.event-courses.title')" :subtitle="t('pages.event-courses.subtitle')">
    <div v-if="hasPermission(StationPermission.EVENT_EDIT)" class="flex items-center justify-end mb-4">
      <PrimaryButton :icon="['fas', 'plus']" :to="{name: 'event-course-new'}" data-testid="course-create">
        {{ t('courses.create') }}
      </PrimaryButton>
    </div>
    <AsyncSection
        :loading="loading"
        :failure="failure"
        :empty="courses.length === 0"
        :empty-message="t('courses.empty')"
        empty-compact
    >
      <ul class="space-y-2">
        <li v-for="course in courses" :key="course.id">
          <CourseListItem :course="course"/>
        </li>
      </ul>
    </AsyncSection>
  </ViewContent>
</template>
