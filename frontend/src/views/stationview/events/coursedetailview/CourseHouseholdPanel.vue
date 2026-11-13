/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import RegistrationStatusBadge from '../eventshared/RegistrationStatusBadge.vue'
import CourseSignUpDialog from './CourseSignUpDialog.vue'
import CourseShortfall from './CourseShortfall.vue'
import CourseAnswersButton from './CourseAnswersButton.vue'
import RegistrationFieldAnswers from '../eventdetailview/RegistrationFieldAnswers.vue'
import type {CourseDetail, CourseSignUp} from '@/api/generated/schema'
import {RegistrationStatus} from '@/api/generated/schema'
import {eventCourses} from '@/api'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * The sign-up of the reader and of everybody they look after: whether each is signed up, which parts
 * they chose, and the buttons to sign up, change the parts or withdraw.
 */
const props = defineProps<{
  detail: CourseDetail
  /** Called after a sign-up changed, so the page reloads what it shows. */
  onChanged?: () => void | Promise<void>
}>()

const {t} = useI18n()

const dialogFor = ref<CourseSignUp | null>(null)
const withdrawFor = ref<CourseSignUp | null>(null)
const withdrawOpen = ref(false)
const busy = ref(false)
const failure = ref<Failure | null>(null)

/** Whether sign-ups closed with the deadline. */
const deadlinePassed = computed(() => {
  const deadline = props.detail.course.registrationDeadline
  return deadline != null && new Date(deadline).getTime() < Date.now()
})

/** Whether no part is left to choose: none yet, or every one past or called off. */
const nothingLeft = computed(() =>
    props.detail.parts.length === 0 || props.detail.parts.every(part => part.tookPlace || part.calledOff))

/** Why parts can no longer be changed, or null while they can. */
const changeClosed = computed(() => {
  if (deadlinePassed.value) return t('courses.closedDeadline')
  if (props.detail.parts.length === 0) return t('courses.closedNoParts')
  if (nothingLeft.value) return t('courses.closedOver')
  return null
})

/** Why nobody can newly sign up, or null while the course takes sign-ups; a full course closes here too. */
const signUpClosed = computed(() => {
  if (changeClosed.value) return changeClosed.value
  const course = props.detail.course
  if (course.registrationLimit != null && course.holding >= course.registrationLimit) return t('courses.closedFull')
  return null
})

/** Whether a sign-up holds or waits for a place. */
function active(entry: CourseSignUp): boolean {
  return entry.status === RegistrationStatus.PENDING || entry.status === RegistrationStatus.ACCEPTED
}

function askWithdraw(entry: CourseSignUp) {
  withdrawFor.value = entry
  withdrawOpen.value = true
}

async function confirmWithdraw() {
  const entry = withdrawFor.value
  if (!entry) return
  busy.value = true
  try {
    await eventCourses.withdraw(props.detail.course.id, entry.memberId)
    withdrawOpen.value = false
    await props.onChanged?.()
  } catch (e) {
    failure.value = describeFailure(e, t)
    withdrawOpen.value = false
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="course-household">
    <SectionHeader>{{ t('courses.household') }}</SectionHeader>
    <FailureAlert v-if="failure" :failure="failure"/>
    <div v-for="entry in detail.household" :key="entry.memberId"
         class="flex flex-wrap items-center justify-between gap-2" data-testid="course-household-entry">
      <div class="flex flex-wrap items-center gap-2 min-w-0">
        <MemberName :identity="entry.member"/>
        <RegistrationStatusBadge v-if="entry.status" :status="entry.status"/>
        <MutedText v-else tag="span" size="sm">{{ t('courses.notSignedUp') }}</MutedText>
        <MutedText v-if="active(entry)" tag="span" size="sm">{{ t('courses.chosen', {count: entry.parts.length}) }}</MutedText>
        <CourseShortfall v-if="active(entry)" :sets="entry.sets"/>
        <ErrorBadge v-if="entry.answersMissing" data-testid="course-answer-missing">{{ t('eventDetail.answerMissing') }}</ErrorBadge>
        <RegistrationFieldAnswers v-if="active(entry)" :fields="detail.questions" :values="entry.answers" class="basis-full"/>
      </div>
      <MutedText v-if="!entry.mayRegister && !active(entry)" tag="span" size="sm">
        {{ t('courses.notMeantFor', {name: entry.member.name ?? ''}) }}
      </MutedText>
      <ButtonRow v-else-if="!detail.course.cancelled" align="end">
        <template v-if="!active(entry)">
          <MutedText v-if="signUpClosed" tag="span" size="sm" data-testid="course-closed">{{ signUpClosed }}</MutedText>
          <PrimaryButton v-else :icon="['fas', 'check']" data-testid="course-sign-up" @click="dialogFor = entry">
            {{ t('courses.signUp') }}
          </PrimaryButton>
        </template>
        <template v-else>
          <SecondaryButton
              v-if="!changeClosed"
              :icon="['fas', 'pen']"
              data-testid="course-change-parts"
              @click="dialogFor = entry"
          >
            {{ t('courses.changeParts') }}
          </SecondaryButton>
          <CourseAnswersButton v-if="!changeClosed && detail.questions.length > 0" :course-id="detail.course.id"
                               :entry="entry" :questions="detail.questions" @saved="onChanged?.()"/>
          <SecondaryButton :icon="['fas', 'xmark']" data-testid="course-withdraw" @click="askWithdraw(entry)">
            {{ t('courses.withdraw') }}
          </SecondaryButton>
        </template>
      </ButtonRow>
    </div>
    <CourseSignUpDialog
        v-if="dialogFor"
        :detail="detail"
        :entry="dialogFor"
        @close="dialogFor = null"
        @saved="dialogFor = null; onChanged?.()"
    />
    <ConfirmDeleteModal
        v-model="withdrawOpen"
        :title="t('courses.withdrawTitle')"
        :message="t('courses.withdrawText', {name: withdrawFor?.member.name ?? ''})"
        :confirm-label="t('courses.withdraw')"
        :busy="busy"
        @confirm="confirmWithdraw"
    />
  </NeutralContainer>
</template>
