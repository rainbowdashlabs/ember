/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import RegistrationStatusBadge from '../eventshared/RegistrationStatusBadge.vue'
import CourseShortfall from './CourseShortfall.vue'
import CourseSignUpDialog from './CourseSignUpDialog.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {fromCompletion} from '@/components/input/select/memberOption'
import {useSession} from '@/composables/useSession'
import type {CourseDetail, CourseSignUp, MemberCompletion} from '@/api/generated/schema'
import {RegistrationStatus, StationPermission} from '@/api/generated/schema'
import {eventCourses, stationMembers} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * Every sign-up of a course for whoever handles registrations: who, in which status, how many parts,
 * whether the parts fall short of a set, and accepting or turning down a sign-up with all its parts.
 * Whoever runs events also signs somebody else up here, past the deadline and the rules if need be;
 * they sign themselves up like every member.
 */
const props = defineProps<{
  detail: CourseDetail
  /** Called after a decision, so the page reloads its counts. */
  onChanged?: () => void | Promise<void>
}>()

const {t} = useI18n()

const signUps = ref<CourseSignUp[]>([])
const members = ref<MemberCompletion[]>([])
const picked = ref('')
const adding = ref<CourseSignUp | null>(null)
const {hasPermission, sessionInfo} = useSession()
const canAdd = computed(() => hasPermission(StationPermission.EVENT_MANAGER) && !props.detail.course.cancelled)

/** The station's members who hold no sign-up yet, offered for signing up. */
const options = computed(() => {
  const taken = new Set(signUps.value.filter(entry => entry.status === RegistrationStatus.PENDING
      || entry.status === RegistrationStatus.ACCEPTED).map(entry => entry.memberId))
  const self = sessionInfo.value?.member?.id
  return members.value.filter(member => !taken.has(member.id) && member.id !== self).map(fromCompletion)
})

/** Opens the sign-up dialog for the picked member, as whoever runs events. */
function startAdding(value: string) {
  const member = members.value.find(candidate => String(candidate.id) === value)
  if (!member) return
  adding.value = {memberId: member.id, member, status: null, registeredAt: null, parts: [], sets: [], mayRegister: true}
  picked.value = ''
}

async function added() {
  adding.value = null
  await props.onChanged?.()
}
const actionFailure = ref<Failure | null>(null)
const deciding = ref<number | null>(null)

const {loading, failure, reload} = useAsyncLoader(async () => {
  const [entries, completions] = await Promise.all([
    eventCourses.listSignUps(props.detail.course.id),
    canAdd.value ? stationMembers.listCompletions().catch(() => []) : Promise.resolve([]),
  ])
  signUps.value = entries
  members.value = completions
})

watch(() => props.detail.course.id, () => reload())

async function decide(entry: CourseSignUp, status: RegistrationStatus) {
  deciding.value = entry.memberId
  actionFailure.value = null
  try {
    await eventCourses.decide(props.detail.course.id, entry.memberId, status)
    await props.onChanged?.()
  } catch (e) {
    actionFailure.value = describeFailure(e, t)
  } finally {
    deciding.value = null
  }
}

defineExpose({reload})
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="course-sign-ups">
    <SectionHeader>{{ t('courses.signUps') }}</SectionHeader>
    <FailureAlert v-if="actionFailure" :failure="actionFailure"/>
    <div v-if="canAdd" class="space-y-1" data-testid="course-add-person">
      <SubHeader>{{ t('courses.addPerson') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('courses.addPersonHint') }}</MutedText>
      <MemberSelectInput
          :model-value="picked"
          :members="options"
          :placeholder="t('courses.choosePerson')"
          @update:model-value="startAdding"
      />
    </div>
    <CourseSignUpDialog v-if="adding" :detail="detail" :entry="adding" override
                        @close="adding = null" @saved="added"/>
    <AsyncSection :loading="loading" :failure="failure" :empty="signUps.length === 0"
                  :empty-message="t('courses.signUpsEmpty')" empty-compact>
      <ul class="divide-y divide-(--border)">
        <li v-for="entry in signUps" :key="entry.memberId" class="flex flex-wrap items-center justify-between gap-2 py-2"
            data-testid="course-sign-up-row">
          <div class="flex flex-wrap items-center gap-2 min-w-0">
            <MemberName :identity="entry.member"/>
            <RegistrationStatusBadge v-if="entry.status" :status="entry.status"/>
            <MutedText tag="span" size="sm">{{ t('courses.chosen', {count: entry.parts.length}) }}</MutedText>
            <CourseShortfall :sets="entry.sets"/>
          </div>
          <ButtonRow pair align="end">
            <SuccessButton v-if="entry.status !== RegistrationStatus.ACCEPTED" compact :disabled="deciding === entry.memberId"
                           data-testid="course-accept" @click="decide(entry, RegistrationStatus.ACCEPTED)">
              {{ t('courses.accept') }}
            </SuccessButton>
            <ErrorButton v-if="entry.status !== RegistrationStatus.DENIED" compact :disabled="deciding === entry.memberId"
                         data-testid="course-deny" @click="decide(entry, RegistrationStatus.DENIED)">
              {{ t('courses.deny') }}
            </ErrorButton>
          </ButtonRow>
        </li>
      </ul>
    </AsyncSection>
  </NeutralContainer>
</template>
