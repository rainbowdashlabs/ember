/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {SignupMemberSet} from '@/composables/useSignupMemberSet'

/**
 * What the set holds and what it had to leave behind, said before anything is created.
 *
 * <p>A count that is quietly smaller than the one on the screen behind it is the thing that makes
 * somebody distrust the whole list, so the two groups that cannot be carried over are named here
 * rather than discovered afterwards by counting rows.
 */
defineProps<{
  memberSet: SignupMemberSet
  /** The evening the set belongs to, already written the way a reader reads a date. */
  dateLabel: string
  /**
   * Whether what is being made stays tied to this evening instead of copying it once.
   *
   * <p>The closing sentence is the one people read hardest, and it has to say the opposite thing in
   * each case: a copy never catches up, while a list that follows the evening does, but only when
   * somebody presses the refresh button on it.
   */
  following?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-1">
    <MutedText tag="p" size="sm">
      {{ t('signupLists.setSummary', {count: memberSet.count, date: dateLabel}) }}
    </MutedText>
    <MutedText v-if="memberSet.guestCount > 0" tag="p" size="sm">
      {{ t('signupLists.guestsLeftOut', {count: memberSet.guestCount}) }}
    </MutedText>
    <MutedText v-if="memberSet.formerCount > 0" tag="p" size="sm">
      {{ t('signupLists.formerLeftOut', {count: memberSet.formerCount}) }}
    </MutedText>
    <Alert variant="info" class="mt-2">
      {{ following ? t('signupLists.following') : t('signupLists.snapshot') }}
    </Alert>
  </div>
</template>
