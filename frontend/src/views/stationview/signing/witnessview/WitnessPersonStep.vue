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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SigningStepFrame from '../signingview/SigningStepFrame.vue'
import {StationUserType} from '@/api/generated/schema'
import type {WitnessPersonResponse} from '@/api/generated/schema'
import {peopleMatching} from './witnessFlow'

/**
 * The first screen of signing in front of a witness: the people the witness may collect signatures from,
 * found by name, a trial user marked as such. Picking one leads to what they may sign.
 */
const props = defineProps<{
  people: WitnessPersonResponse[]
  position: number
  total: number
}>()

defineEmits<{pick: [person: WitnessPersonResponse]}>()

const {t} = useI18n()
const search = ref('')

const shown = computed(() => peopleMatching(props.people, search.value))
</script>

<template>
  <SigningStepFrame :title="t('signing.witness.person.heading')" :position="position" :total="total">
    <MutedText tag="p" size="sm">{{ t('signing.witness.person.hint') }}</MutedText>
    <SearchInput v-model="search" :placeholder="t('signing.witness.person.search')" :aria-label="t('signing.witness.person.search')"
                 data-testid="witness-person-search"/>
    <MutedText v-if="shown.length === 0" tag="p" size="sm">{{ t('signing.witness.person.none') }}</MutedText>
    <ul class="space-y-2" data-testid="witness-people">
      <li v-for="person in shown" :key="person.memberId">
        <SecondaryButton type="button" class="w-full justify-start" :data-testid="`witness-person-${person.memberId}`"
                         @click="$emit('pick', person)">
          {{ person.name }}
          <SecondaryBadge v-if="person.userType === StationUserType.TRIAL">{{ t('signing.witness.person.trial') }}</SecondaryBadge>
        </SecondaryButton>
      </li>
    </ul>
  </SigningStepFrame>
</template>
