/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import ResultFilterControls from './ResultFilterControls.vue'
import ResultGroupingControls from './ResultGroupingControls.vue'
import type {ResultFilter, ResultGrouping} from '@/api/forms'
import type {ProfileField} from '@/api/profileFields'
import type {MemberGroup, UserTag} from '@/api/types'

/**
 * Above the charts: which respondents count, and what to split them by. `matching` is how many
 * responses the current choice lets through, shown while anything is chosen.
 */
defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
  fields: ProfileField[]
  matching: number | null
  querying: boolean
}>()

const filter = defineModel<ResultFilter>('filter', {required: true})
const grouping = defineModel<ResultGrouping | null>('grouping', {required: true})

const emit = defineEmits<{
  reset: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <SubHeader>{{ t('forms.analytics.grouping.title') }}</SubHeader>
      <div class="flex items-center gap-3">
        <Spinner v-if="querying" size="sm"/>
        <MutedText v-else-if="matching !== null" size="sm">{{ t('forms.analytics.grouping.matching', {count: matching}) }}</MutedText>
        <SecondaryButton v-if="matching !== null" :icon="['fas', 'rotate-left']" compact @click="emit('reset')">
          {{ t('forms.analytics.grouping.reset') }}
        </SecondaryButton>
      </div>
    </div>
    <div class="space-y-2">
      <MutedText size="sm" tag="p">{{ t('forms.analytics.grouping.filter') }}</MutedText>
      <ResultFilterControls v-model="filter" :groups="groups" :tags="tags" :fields="fields"/>
    </div>
    <div class="space-y-2">
      <MutedText size="sm" tag="p">{{ t('forms.analytics.grouping.groupBy') }}</MutedText>
      <ResultGroupingControls v-model="grouping" :groups="groups" :tags="tags" :fields="fields"/>
    </div>
  </NeutralContainer>
</template>
