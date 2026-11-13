/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {until} from '@vueuse/core'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CourseSetsEditor from './courseeditview/CourseSetsEditor.vue'
import CourseDetailsForm from './courseeditview/CourseDetailsForm.vue'
import type {SetRow} from './courseeditview/courseSetRow'
import CoursePartsEditor from './courseeditview/CoursePartsEditor.vue'
import RegistrationFieldsEditor from './eventshared/RegistrationFieldsEditor.vue'
import {asSaved} from './eventshared/eventQuestions'
import type {CourseDetail, MemberGroup, RegistrationFieldDefinition, RestrictionAudience, UserTag} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'
import {eventCourses, memberGroups as memberGroupsApi, userTags as userTagsApi} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure, type Failure} from '@/util/failure'
import {instantToLocalInput, localInputToInstant} from '@/util/format'
import {showToast} from '@/util/toast'

/**
 * Creates or edits a course: its name, description and sign-up (places, deadline, confirmation),
 * its sets with their minimums, who may see and sign up, and, once it exists, its parts. Saving
 * stores all of it in one request, so a refusal leaves nothing half saved, and hands the sign-up and
 * the audiences down to every part. Changing parts reads only the parts again and leaves unsaved
 * edits in the form alone.
 */
const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()

const courseId = computed(() => (route.params.courseId ? Number(route.params.courseId) : null))
const detail = ref<CourseDetail | null>(null)
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])

const name = ref('')
const description = ref('')
const limit = ref<number | undefined>(undefined)
const deadline = ref('')
const confirm = ref(false)
const sets = ref<SetRow[]>([{id: null, name: t('courses.defaultSet'), minimum: 1}])
const registration = ref<RestrictionSelection>(emptySelection())
const view = ref<RestrictionSelection>(emptySelection())
const questions = ref<RegistrationFieldDefinition[]>([])
const saving = ref(false)
const saveFailure = ref<Failure | null>(null)

function emptySelection(): RestrictionSelection {
  return {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'}
}

function selectionOf(audience: RestrictionAudience): RestrictionSelection {
  return {
    userTypes: [...audience.userTypes],
    groupIds: [...audience.groupIds],
    tagIds: [...audience.tagIds],
    memberIds: [...audience.memberIds],
    mode: audience.mode,
  }
}

/** Fills the form from the stored course. */
function fill(loadedDetail: CourseDetail) {
  detail.value = loadedDetail
  name.value = loadedDetail.course.name
  description.value = loadedDetail.course.description ?? ''
  limit.value = loadedDetail.course.registrationLimit ?? undefined
  deadline.value = instantToLocalInput(loadedDetail.course.registrationDeadline)
  confirm.value = loadedDetail.course.requiresConfirmation
  sets.value = loadedDetail.sets.map(set => ({id: set.id, name: set.name, minimum: set.minimum}))
  registration.value = selectionOf(loadedDetail.registration)
  view.value = selectionOf(loadedDetail.view)
  questions.value = loadedDetail.questions.map(({name, fieldType, config, overview}) => ({name, fieldType, config, overview}))
}

function savedQuestions(): RegistrationFieldDefinition[] {
  return questions.value.filter(question => question.name?.trim()).map(asSaved)
}

const {loading, failure} = useAsyncLoader(async () => {
  await until(loaded).toBe(true)
  const [memberGroups, userTags] = await Promise.all([
    memberGroupsApi.listGroups(),
    userTagsApi.listChoosableTags(),
  ])
  groups.value = memberGroups
  tags.value = userTags
  if (courseId.value != null) fill(await eventCourses.getCourse(courseId.value))
})

/** Reads the course again after a part changed, touching nothing the form holds unsaved. */
async function refreshParts() {
  if (courseId.value == null) return
  detail.value = await eventCourses.getCourse(courseId.value)
}

function details() {
  return {
    name: name.value,
    description: description.value.trim() === '' ? null : description.value,
    registrationLimit: limit.value ?? null,
    registrationDeadline: deadline.value ? localInputToInstant(deadline.value) : null,
    requiresConfirmation: confirm.value,
  }
}

async function save() {
  saving.value = true
  saveFailure.value = null
  try {
    const drafts = sets.value.map(set => ({id: set.id, name: set.name, minimum: set.minimum}))
    if (courseId.value == null) {
      const created = await eventCourses.createCourse({
        details: details(),
        sets: drafts,
        registration: registration.value,
        view: view.value,
        questions: savedQuestions(),
      })
      showToast(t('courses.saved'), 'success')
      await router.replace({name: 'event-course-edit', params: {courseId: created.course.id}})
      fill(created)
      return
    }
    fill(await eventCourses.updateCourse(courseId.value, details(), drafts, {
      registration: registration.value,
      view: view.value,
    }, savedQuestions()))
  } catch (e) {
    saveFailure.value = describeFailure(e, t)
    throw e
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ViewContent
      :title="courseId == null ? t('pages.event-course-new.title') : t('pages.event-course-edit.title')"
      :subtitle="courseId == null ? t('pages.event-course-new.subtitle') : t('pages.event-course-edit.subtitle')"
  >
    <AsyncSection :loading="loading" :failure="failure">
      <div class="space-y-6">
        <CourseDetailsForm
            v-model:name="name"
            v-model:description="description"
            v-model:limit="limit"
            v-model:deadline="deadline"
            v-model:confirm="confirm"
        />

        <NeutralContainer class="space-y-3">
          <SectionHeader>{{ t('courses.sets') }}</SectionHeader>
          <MutedText tag="p" size="sm">{{ t('courses.setsHint') }}</MutedText>
          <CourseSetsEditor v-model="sets"/>
        </NeutralContainer>

        <NeutralContainer class="space-y-3">
          <SectionHeader>{{ t('courses.audiences') }}</SectionHeader>
          <MutedText tag="p" size="sm">{{ t('courses.audienceHint') }}</MutedText>
          <FieldLabel>{{ t('courses.registerAudience') }}</FieldLabel>
          <RestrictionsField v-model="registration" :groups="groups" :tags="tags"/>
          <FieldLabel>{{ t('courses.viewAudience') }}</FieldLabel>
          <RestrictionsField v-model="view" :groups="groups" :tags="tags"/>
        </NeutralContainer>

        <NeutralContainer class="space-y-3">
          <SectionHeader>{{ t('courses.questions') }}</SectionHeader>
          <MutedText tag="p" size="sm">{{ t('courses.questionsHint') }}</MutedText>
          <RegistrationFieldsEditor v-model="questions"/>
        </NeutralContainer>

        <FailureAlert v-if="saveFailure" :failure="saveFailure"/>
        <ButtonRow pair align="end">
          <SecondaryButton
              :to="courseId == null ? {name: 'event-courses'} : {name: 'event-course', params: {courseId}}"
          >
            {{ t('common.cancel') }}
          </SecondaryButton>
          <SaveButton :action="save" :disabled="saving" data-testid="course-save"/>
        </ButtonRow>

        <NeutralContainer class="space-y-3">
          <SectionHeader>{{ t('courses.parts') }}</SectionHeader>
          <MutedText v-if="!detail" tag="p" size="sm">{{ t('courses.saveFirst') }}</MutedText>
          <CoursePartsEditor v-else :detail="detail" :on-changed="refreshParts"/>
        </NeutralContainer>
      </div>
    </AsyncSection>
  </ViewContent>
</template>
