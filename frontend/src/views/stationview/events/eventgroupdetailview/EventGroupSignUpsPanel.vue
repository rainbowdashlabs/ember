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
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import {useDataTable} from '@/composables/useDataTable'
import {byValue} from '@/composables/useSortable'
import EventGroupAnswersButton from './EventGroupAnswersButton.vue'
import RegistrationStatusBadge from '../eventshared/RegistrationStatusBadge.vue'
import EventGroupShortfall from './EventGroupShortfall.vue'
import EventGroupSignUpDialog from './EventGroupSignUpDialog.vue'
import EventGroupSignUpExport from './EventGroupSignUpExport.vue'
import RegistrationFieldAnswers from '../eventdetailview/RegistrationFieldAnswers.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {fromCompletion} from '@/components/input/select/memberOption'
import {useSession} from '@/composables/useSession'
import type {EventGroupDetail, EventGroupSignUp, MemberCompletion} from '@/api/generated/schema'
import {RegistrationStatus, StationPermission} from '@/api/generated/schema'
import {holdsOrWaitsForPlace} from '@/util/eventGroupChoice'
import {isSignUpComplete, signUpColumns} from './eventGroupTables'
import {eventGroups, stationMembers} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * Every sign-up of an event group for whoever handles registrations: who, in which status, when, how many
 * parts, whether the sign-up is complete, and accepting or turning down a sign-up with all its parts.
 * Whoever runs events also signs somebody else up here, past the deadline and the rules if need be;
 * they sign themselves up like every member.
 *
 * <p>The list is a table of records, so it sorts and filters by every column and shows a card per
 * sign-up on a phone. A sign-up is complete when its parts meet every set and no answer is owed, and
 * filtering that column to "incomplete" leaves the ones somebody still has to chase.
 */
const props = defineProps<{
  detail: EventGroupDetail
  /** Called after a decision, so the page reloads its counts. */
  onChanged?: () => void | Promise<void>
}>()

const {t} = useI18n()

const signUps = ref<EventGroupSignUp[]>([])
const members = ref<MemberCompletion[]>([])
const picked = ref('')
const adding = ref<EventGroupSignUp | null>(null)
const {hasPermission, sessionInfo} = useSession()
/** Whoever runs events signs others up and corrects their answers, every question included, as the server allows. */
const canAdd = computed(() => hasPermission(StationPermission.EVENT_MANAGER) && !props.detail.eventGroup.cancelled)

/** The station's members who hold no sign-up yet, offered for signing up. */
const options = computed(() => {
  const taken = new Set(signUps.value.filter(entry => holdsOrWaitsForPlace(entry.status)).map(entry => entry.memberId))
  const self = sessionInfo.value?.member?.id
  return members.value.filter(member => !taken.has(member.id) && member.id !== self).map(fromCompletion)
})

/** Opens the sign-up dialog for the picked member, as whoever runs events. */
function startAdding(value: string) {
  const member = members.value.find(candidate => String(candidate.id) === value)
  if (!member) return
  adding.value = {memberId: member.id, member, status: null, registeredAt: null, parts: [], sets: [], answers: [], answersMissing: false, mayRegister: true}
  picked.value = ''
}

async function added() {
  adding.value = null
  await props.onChanged?.()
}

const {loading, failure, reload} = useAsyncLoader(async () => {
  const [entries, completions] = await Promise.all([
    eventGroups.listSignUps(props.detail.eventGroup.id),
    canAdd.value ? stationMembers.listCompletions().catch(() => []) : Promise.resolve([]),
  ])
  signUps.value = entries
  members.value = completions
})

watch(() => props.detail.eventGroup.id, () => reload())

/**
 * Accepts or declines one sign-up. While one decision is on its way every decision button waits, so
 * a second click is not dropped without a word.
 */
const {running: deciding, failure: actionFailure, run: decide} = useAsyncAction(
    async (entry: EventGroupSignUp, status: RegistrationStatus) => {
      await eventGroups.decide(props.detail.eventGroup.id, entry.memberId, status)
      await props.onChanged?.()
    })

const columns = computed(() => signUpColumns(t))

const table = useDataTable<EventGroupSignUp>({
  id: 'event-group-sign-ups',
  rows: signUps,
  columns,
  rowKey: entry => entry.memberId,
  fallbackSort: byValue(entry => entry.member.name ?? ''),
})

defineExpose({reload})
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="event-group-sign-ups">
    <div class="flex flex-wrap items-start justify-between gap-2">
      <SectionHeader>{{ t('eventGroups.signUps') }}</SectionHeader>
      <ButtonRow align="end">
        <TableColumnPicker :table="table"/>
        <EventGroupSignUpExport :event-group-id="detail.eventGroup.id"/>
      </ButtonRow>
    </div>
    <FailureAlert v-if="actionFailure" :failure="actionFailure"/>
    <div v-if="canAdd" class="space-y-1" data-testid="event-group-add-person">
      <SubHeader>{{ t('eventGroups.addPerson') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('eventGroups.addPersonHint') }}</MutedText>
      <MemberSelectInput
          :model-value="picked"
          :members="options"
          :placeholder="t('eventGroups.choosePerson')"
          @update:model-value="startAdding"
      />
    </div>
    <EventGroupSignUpDialog v-if="adding" :detail="detail" :entry="adding" override
                        @close="adding = null" @saved="added"/>
    <AsyncSection :loading="loading" :failure="failure" :empty="signUps.length === 0"
                  :empty-message="t('eventGroups.signUpsEmpty')" empty-compact>
      <RecordTable :table="table" test-id="event-group-sign-ups-table" row-test-id="event-group-sign-up-row" plain>
        <template #cell-member="{row}">
          <MemberName :identity="row.member"/>
        </template>
        <template #cell-status="{row}">
          <RegistrationStatusBadge v-if="row.status" :status="row.status"/>
        </template>
        <template #cell-complete="{row}">
          <div class="flex flex-wrap gap-1">
            <SuccessBadge v-if="isSignUpComplete(row)">{{ t('eventGroups.signUpComplete') }}</SuccessBadge>
            <EventGroupShortfall :sets="row.sets"/>
            <ErrorBadge v-if="row.answersMissing" data-testid="event-group-answer-missing">{{ t('eventDetail.answerMissing') }}</ErrorBadge>
          </div>
        </template>
        <template #after-row="{row, span}">
          <tr v-if="row.answers.length > 0 && detail.questions.length > 0">
            <td :colspan="span" class="px-4 pb-3">
              <RegistrationFieldAnswers :fields="detail.questions" :values="row.answers"/>
            </td>
          </tr>
        </template>
        <template #card-extra="{row}">
          <RegistrationFieldAnswers v-if="row.answers.length > 0" :fields="detail.questions" :values="row.answers"/>
        </template>
        <template #actions="{row}">
          <ButtonRow align="end">
            <EventGroupAnswersButton v-if="canAdd && holdsOrWaitsForPlace(row.status) && detail.questions.length > 0"
                                     :event-group-id="detail.eventGroup.id" :entry="row" :questions="detail.questions"
                                     @saved="onChanged?.()"/>
            <SuccessButton v-if="row.status !== RegistrationStatus.ACCEPTED" compact :disabled="deciding"
                           data-testid="event-group-accept" @click="decide(row, RegistrationStatus.ACCEPTED)">
              {{ t('eventGroups.accept') }}
            </SuccessButton>
            <ErrorButton v-if="row.status !== RegistrationStatus.DENIED" compact :disabled="deciding"
                         data-testid="event-group-deny" @click="decide(row, RegistrationStatus.DENIED)">
              {{ t('eventGroups.deny') }}
            </ErrorButton>
          </ButtonRow>
        </template>
      </RecordTable>
    </AsyncSection>
  </NeutralContainer>
</template>
