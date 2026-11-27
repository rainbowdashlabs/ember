/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, nextTick, ref, useId, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import * as members from '@/api/members'
import {FindableBecause, type CandidateResponse} from '@/api/generated/schema'
import TextInput from '@/components/input/text/TextInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {useDebouncedSearch} from '@/composables/useDebouncedSearch'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * Finds somebody who already has an account on this instance by their name, so they can be invited
 * without anybody knowing their address. Picking a person hands them to the wizard, which then asks
 * them to link their account instead of inviting an address.
 *
 * <p>The search only answers once a word of three letters is typed, which is what the server asks for
 * too. People already at the station, already asked, or who said no lately are listed but cannot be
 * picked, so the reader sees why somebody they expected is not offered. A search that fails says so
 * rather than reporting nobody, which would send the reader to the address for somebody who exists.
 *
 * <p>Focus follows the choice: to the button that clears it once somebody is picked, and back to the
 * search once it is cleared, since the control that had it is gone either way.
 */
const {t} = useI18n()

const found = defineModel<CandidateResponse | null>('found', {required: true})

const MIN_LETTERS = 3

const inputId = useId()
const hintId = useId()
const clearId = useId()
const failure = ref<Failure | null>(null)

function hasSearchableWord(text: string): boolean {
  return text.trim().split(/\s+/).some(word => word.length >= MIN_LETTERS)
}

const {query, results, searching, onInput} = useDebouncedSearch<CandidateResponse>(async (search): Promise<CandidateResponse[]> => {
  failure.value = null
  if (!hasSearchableWord(search)) return []
  try {
    const answer = await members.searchInviteCandidates(search)
    return hasSearchableWord(query.value) && !found.value ? answer : []
  } catch (e) {
    failure.value = describeFailure(e, t)
    return []
  }
})

const tooShort = computed(() => query.value.trim().length > 0 && !hasSearchableWord(query.value))
const nobody = computed(() =>
  !searching.value && !failure.value && hasSearchableWord(query.value) && results.value.length === 0)

/** What ties the reader to this person, so two people of one name can be told apart. */
function tieOf(candidate: CandidateResponse): string {
  if (candidate.because === FindableBecause.OPTED_IN) return t('membersCreate.findableOptedIn')
  if (candidate.because === FindableBecause.SHARED_ASSOCIATION) {
    return t('membersCreate.findableAssociation', {name: candidate.through ?? ''})
  }
  return t('membersCreate.findableStation', {name: candidate.through ?? ''})
}

/** Why this person cannot be picked, or null where they can. */
function blockedBecause(candidate: CandidateResponse): string | null {
  if (candidate.alreadyHere) return t('membersCreate.candidateHere')
  if (candidate.alreadyAsked) return t('membersCreate.candidateAsked')
  if (candidate.declinedLately) return t('membersCreate.candidateDeclined')
  return null
}

async function focus(id: string) {
  await nextTick()
  document.getElementById(id)?.focus()
}

function pick(candidate: CandidateResponse) {
  found.value = candidate
  void focus(clearId)
}

function clear() {
  found.value = null
  void focus(inputId)
}

watch(found, () => {
  query.value = ''
  results.value = []
  failure.value = null
})
</script>

<template>
  <div class="space-y-2" data-testid="invite-person-search">
    <template v-if="found">
      <Alert variant="info">
        {{ t('membersCreate.foundChosen', {name: `${found.firstName} ${found.lastName}`}) }}
        {{ t('membersCreate.foundChosenHint') }}
      </Alert>
      <SecondaryButton :id="clearId" :icon="['fas', 'xmark']" @click="clear">{{ t('membersCreate.foundClear') }}</SecondaryButton>
    </template>
    <template v-else>
      <FieldLabel :for="inputId">{{ t('membersCreate.findPerson') }}</FieldLabel>
      <TextInput
          :id="inputId"
          v-model="query"
          :aria-describedby="hintId"
          :placeholder="t('membersCreate.findPersonPlaceholder')"
          @input="onInput"
      />
      <MutedText :id="hintId" tag="p" size="xs">{{ t('membersCreate.findPersonHint') }}</MutedText>
      <div aria-live="polite" class="space-y-2" role="status">
        <MutedText v-if="tooShort" tag="p" size="xs">{{ t('membersCreate.findPersonTooShort') }}</MutedText>
        <Spinner v-if="searching" size="sm"/>
        <MutedText v-else-if="nobody" tag="p" size="sm">{{ t('membersCreate.findPersonNone') }}</MutedText>
        <MutedText v-if="results.length > 0" tag="p" size="xs">
          {{ t('membersCreate.findPersonCount', {count: results.length}) }}
        </MutedText>
      </div>
      <FailureAlert :failure="failure"/>
      <ul v-if="results.length > 0" class="divide-y divide-(--border) rounded-theme border border-(--border)">
        <li
            v-for="candidate in results"
            :key="candidate.accountUid"
            class="flex flex-wrap items-center justify-between gap-2 px-3 py-2"
            data-testid="invite-candidate"
        >
          <div>
            <p class="text-sm font-medium">{{ candidate.firstName }} {{ candidate.lastName }}</p>
            <MutedText tag="p" size="xs">{{ blockedBecause(candidate) ?? tieOf(candidate) }}</MutedText>
          </div>
          <SecondaryButton
              :aria-label="t('membersCreate.findPersonPickNamed', {name: `${candidate.firstName} ${candidate.lastName}`})"
              :disabled="blockedBecause(candidate) !== null"
              @click="pick(candidate)"
          >
            {{ t('membersCreate.findPersonPick') }}
          </SecondaryButton>
        </li>
      </ul>
    </template>
  </div>
</template>
