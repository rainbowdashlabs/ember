/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import MemberTypeBadge from '@/views/stationview/members/listview/MemberTypeBadge.vue'
import SigningStepFrame from '../signingview/SigningStepFrame.vue'
import type {WitnessCandidateResponse} from '@/api/generated/schema'
import {useWitnessPeopleTable} from './useWitnessPeopleTable'

/**
 * The first screen of signing in front of a witness: the people the witness may collect signatures from,
 * as the station's member tables show people, with how many signatures wait on each.
 *
 * <p>"Offen" lists those something waits on and is where the page opens; where nothing waits on anybody it
 * opens on "Alle", since a document can still be generated for anyone from a template. Picking a person
 * leads to what they may sign.
 */
const props = defineProps<{
  people: WitnessCandidateResponse[]
  /** The name of each group by its id. */
  groupNames: ReadonlyMap<number, string>
  position: number
  total: number
}>()

defineEmits<{pick: [person: WitnessCandidateResponse]}>()

const {t} = useI18n()

const waiting = computed(() => props.people.filter(person => person.openFields > 0))
const tab = ref<'open' | 'all'>(waiting.value.length > 0 ? 'open' : 'all')
const shown = computed(() => tab.value === 'open' ? waiting.value : props.people)

const table = useWitnessPeopleTable(() => shown.value, () => props.groupNames)
</script>

<template>
  <SigningStepFrame :title="t('signing.witness.person.heading')" :position="position" :total="total" wide>
    <MutedText tag="p" size="sm">{{ t('signing.witness.person.hint') }}</MutedText>
    <div class="flex flex-wrap gap-2">
      <SelectionToggleButton :selected="tab === 'open'" size="md" data-testid="witness-tab-open" @toggle="tab = 'open'">
        {{ t('signing.witness.person.tabOpen', {count: waiting.length}) }}
      </SelectionToggleButton>
      <SelectionToggleButton :selected="tab === 'all'" size="md" data-testid="witness-tab-all" @toggle="tab = 'all'">
        {{ t('signing.witness.person.tabAll', {count: people.length}) }}
      </SelectionToggleButton>
    </div>
    <SearchInput v-model="table.search" :placeholder="t('signing.witness.person.search')"
                 :aria-label="t('signing.witness.person.search')" data-testid="witness-person-search"/>
    <RecordTable :table="table" test-id="witness-people" row-test-id="witness-person-row">
      <template #cell-name="{row}">
        <span class="font-medium"><MemberName :identity="row.identity"/></span>
      </template>
      <template #cell-userType="{row}">
        <MemberTypeBadge :user-type="row.userType"/>
      </template>
      <template #cell-open="{row}">
        <div v-if="row.openFields > 0" class="flex flex-col items-end gap-1">
          <InfoBadge>{{ row.openFields }}</InfoBadge>
          <MutedText size="xs">{{ row.openDocuments.join(', ') }}</MutedText>
        </div>
        <MutedText v-else size="sm">-</MutedText>
      </template>
      <template #actions="{row}">
        <PrimaryButton v-if="row.openFields > 0" compact :data-testid="`witness-person-${row.memberId}`" @click.stop="$emit('pick', row)">
          {{ t('signing.witness.person.collect') }}
        </PrimaryButton>
        <SecondaryButton v-else compact :data-testid="`witness-person-${row.memberId}`" @click.stop="$emit('pick', row)">
          {{ t('signing.witness.person.collect') }}
        </SecondaryButton>
      </template>
      <template #empty>
        <EmptyState>{{ tab === 'open' ? t('signing.witness.person.noneOpen') : t('signing.witness.person.none') }}</EmptyState>
      </template>
    </RecordTable>
  </SigningStepFrame>
</template>
