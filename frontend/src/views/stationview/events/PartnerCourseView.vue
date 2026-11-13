/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import CourseOverview from './coursedetailview/CourseOverview.vue'
import CourseHouseholdPanel from './coursedetailview/CourseHouseholdPanel.vue'
import {partnerCoursePort} from './coursedetailview/courseSignUpPort'
import type {CourseDetail} from '@/api/generated/schema'
import {partnerCourses} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

/**
 * A course a partner station shares with this one: its parts by set and the sign-up of the reader and
 * everybody they look after, sent to the partner that holds it.
 */
const {t} = useI18n()
const route = useRoute()
const {loaded} = useSession()

const stationUid = computed(() => route.params.stationUid as string)
const courseId = computed(() => Number(route.params.courseId))
const detail = ref<CourseDetail | null>(null)
const port = computed(() => partnerCoursePort(stationUid.value, courseId.value))

const {loading, failure, reload} = useAsyncLoader(async () => {
  detail.value = await partnerCourses.getCourse(stationUid.value, courseId.value)
}, {autoLoad: loaded.value})

watch(loaded, ready => { if (ready) reload() })

function partLink(eventId: number) {
  return {name: 'federated-event-detail', params: {stationUid: stationUid.value, eventId}}
}
</script>

<template>
  <ViewContent :title="detail?.course.name ?? t('pages.federated-event-course.title')"
               :subtitle="t('pages.federated-event-course.subtitle')">
    <AsyncSection :loading="loading && !detail" :failure="failure">
      <div v-if="detail" class="space-y-6">
        <CourseOverview :detail="detail" :part-link="partLink"/>
        <CourseHouseholdPanel :detail="detail" :port="port" :on-changed="reload"/>
      </div>
    </AsyncSection>
  </ViewContent>
</template>
