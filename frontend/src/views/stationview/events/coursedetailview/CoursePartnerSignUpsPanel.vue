/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import RegistrationStatusBadge from '../eventshared/RegistrationStatusBadge.vue'
import CourseShortfall from './CourseShortfall.vue'
import type {CourseDetail, CoursePartnerSignUp} from '@/api/generated/schema'
import {RegistrationStatus} from '@/api/generated/schema'
import {eventCourses} from '@/api'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * The sign-ups partner stations' members sent for a shared course, decided the same way as the
 * station's own. Nothing is shown while no partner member signed up.
 */
const props = defineProps<{
  detail: CourseDetail
  /** Called after a decision, so the page reloads its counts. */
  onChanged?: () => void | Promise<void>
}>()

const {t} = useI18n()

const signUps = ref<CoursePartnerSignUp[]>([])
const deciding = ref<number | null>(null)
const failure = ref<Failure | null>(null)

async function reload() {
  try {
    signUps.value = await eventCourses.listPartnerSignUps(props.detail.course.id)
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

async function decide(entry: CoursePartnerSignUp, status: RegistrationStatus) {
  deciding.value = entry.id
  failure.value = null
  try {
    await eventCourses.decidePartner(props.detail.course.id, entry.id, status)
    await reload()
    await props.onChanged?.()
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    deciding.value = null
  }
}

watch(() => props.detail.course.id, reload, {immediate: true})

defineExpose({reload})
</script>

<template>
  <NeutralContainer v-if="signUps.length > 0 || failure" class="space-y-3" data-testid="course-partner-sign-ups">
    <SectionHeader>{{ t('courses.partnerSignUps') }}</SectionHeader>
    <FailureAlert v-if="failure" :failure="failure"/>
    <ul class="divide-y divide-(--border)">
      <li v-for="entry in signUps" :key="entry.id" class="flex flex-wrap items-center justify-between gap-2 py-2"
          data-testid="course-partner-sign-up-row">
        <div class="flex flex-wrap items-center gap-2 min-w-0">
          <MemberName :identity="entry.member ?? null"/>
          <RegistrationStatusBadge v-if="entry.status" :status="entry.status"/>
          <MutedText tag="span" size="sm">{{ t('courses.chosen', {count: entry.parts.length}) }}</MutedText>
          <CourseShortfall :sets="entry.sets"/>
        </div>
        <ButtonRow pair align="end">
          <SuccessButton v-if="entry.status !== RegistrationStatus.ACCEPTED" compact :disabled="deciding === entry.id"
                         data-testid="course-partner-accept" @click="decide(entry, RegistrationStatus.ACCEPTED)">
            {{ t('courses.accept') }}
          </SuccessButton>
          <ErrorButton v-if="entry.status !== RegistrationStatus.DENIED" compact :disabled="deciding === entry.id"
                       data-testid="course-partner-deny" @click="decide(entry, RegistrationStatus.DENIED)">
            {{ t('courses.deny') }}
          </ErrorButton>
        </ButtonRow>
      </li>
    </ul>
  </NeutralContainer>
</template>
