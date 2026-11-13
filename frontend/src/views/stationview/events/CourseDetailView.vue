/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import CourseOverview from './coursedetailview/CourseOverview.vue'
import CourseHouseholdPanel from './coursedetailview/CourseHouseholdPanel.vue'
import CourseSignUpsPanel from './coursedetailview/CourseSignUpsPanel.vue'
import CourseCancelDialog from './coursedetailview/CourseCancelDialog.vue'
import type {CourseDetail} from '@/api/generated/schema'
import {StationPermission} from '@/api/generated/schema'
import {eventCourses} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * One course: what it is, its parts by set with each set's minimum, and the sign-up of the reader and
 * everybody they look after. Whoever handles registrations sees every sign-up and decides on them;
 * whoever edits appointments edits, calls off or deletes the course.
 */
const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded, hasPermission} = useSession()

const courseId = computed(() => Number(route.params.courseId))
const detail = ref<CourseDetail | null>(null)
const actionFailure = ref<Failure | null>(null)
const cancelOpen = ref(false)
const deleteOpen = ref(false)
const busy = ref(false)
const signUpsPanel = ref<InstanceType<typeof CourseSignUpsPanel> | null>(null)

const canEdit = computed(() => hasPermission(StationPermission.EVENT_EDIT))
const canDecide = computed(() => hasPermission(StationPermission.EVENT_REGISTRATION))

const {loading, failure, reload} = useAsyncLoader(async () => {
  detail.value = await eventCourses.getCourse(courseId.value)
}, {autoLoad: loaded.value})

watch(loaded, ready => { if (ready) reload() })

/** Reloads the course and the managers' list after a sign-up or decision changed them. */
async function refresh() {
  await reload()
  await signUpsPanel.value?.reload()
}

async function cancelCourse(reason: string | null) {
  busy.value = true
  try {
    detail.value = await eventCourses.cancelCourse(courseId.value, reason)
    cancelOpen.value = false
  } catch (e) {
    actionFailure.value = describeFailure(e, t)
    cancelOpen.value = false
  } finally {
    busy.value = false
  }
}

async function deleteCourse() {
  busy.value = true
  try {
    await eventCourses.deleteCourse(courseId.value)
    await router.push({name: 'event-courses'})
  } catch (e) {
    actionFailure.value = describeFailure(e, t)
    deleteOpen.value = false
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <ViewContent :title="detail?.course.name ?? t('pages.event-course.title')" :subtitle="t('pages.event-course.subtitle')">
    <AsyncSection :loading="loading && !detail" :failure="failure">
      <div v-if="detail" class="space-y-6">
        <FailureAlert v-if="actionFailure" :failure="actionFailure"/>
        <ButtonRow v-if="canEdit" align="end">
          <SecondaryButton :icon="['fas', 'pen']" :to="{name: 'event-course-edit', params: {courseId}}" data-testid="course-edit">
            {{ t('courses.edit') }}
          </SecondaryButton>
          <SecondaryButton v-if="!detail.course.cancelled" :icon="['fas', 'ban']" data-testid="course-cancel" @click="cancelOpen = true">
            {{ t('courses.cancel') }}
          </SecondaryButton>
          <ErrorButton :icon="['fas', 'trash']" data-testid="course-delete" @click="deleteOpen = true">
            {{ t('courses.delete') }}
          </ErrorButton>
        </ButtonRow>
        <CourseOverview :detail="detail"/>
        <CourseHouseholdPanel :detail="detail" :on-changed="refresh"/>
        <CourseSignUpsPanel v-if="canDecide" ref="signUpsPanel" :detail="detail" :on-changed="refresh"/>
      </div>
    </AsyncSection>
    <CourseCancelDialog v-model="cancelOpen" :busy="busy" @confirm="cancelCourse"/>
    <ConfirmDeleteModal
        v-model="deleteOpen"
        :title="t('courses.deleteTitle')"
        :message="t('courses.deleteText')"
        :busy="busy"
        @confirm="deleteCourse"
    />
  </ViewContent>
</template>
