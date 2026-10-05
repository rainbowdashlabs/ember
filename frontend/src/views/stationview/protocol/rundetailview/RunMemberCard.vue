/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import RunMemberSections from './RunMemberSections.vue'
import type {RunMemberWithProgress, TestProtocolSection} from '@/api/generated/schema'

/**
 * One member of a run: who they are, where they stand and which sections are left.
 *
 * <p>The name and the state share the first line, the sections get a line of their own and the way
 * into grading sits below them, so nothing has to squeeze beside a long name on a phone.
 */
defineProps<{
  entry: RunMemberWithProgress
  name: string
  /** Who holds the member for grading, empty when nobody does. */
  lockedBy: string
  sections: TestProtocolSection[]
  examinerNames: (sectionId: number) => string[]
  canGrade: boolean
}>()

const emit = defineEmits<{
  grade: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-2">
    <div class="flex items-start gap-2">
      <div class="flex-1 min-w-0">
        <div class="font-medium break-words">{{ name }}</div>
        <div class="text-xs text-[var(--text-muted)] flex flex-wrap items-center gap-x-2">
          <span>{{ entry.sectionsDone }}/{{ entry.sectionsTotal }} {{ t('protocol.sections') }}</span>
          <span v-if="lockedBy">
            <font-awesome-icon :icon="['fas', 'lock']" class="mr-1"/>{{ lockedBy }}
          </span>
        </div>
      </div>
      <div class="flex shrink-0 items-center gap-2">
        <span class="font-mono text-sm">{{ entry.member.totalScore }}P</span>
        <SuccessBadge v-if="entry.member.completed">{{ t('protocol.completed') }}</SuccessBadge>
        <ErrorBadge v-else-if="entry.member.lockedBy">{{ t('protocol.locked') }}</ErrorBadge>
        <SecondaryBadge v-else>{{ t('protocol.pending') }}</SecondaryBadge>
      </div>
    </div>
    <RunMemberSections :sections="sections" :done-section-ids="entry.doneSectionIds" :examiner-names="examinerNames"/>
    <div v-if="canGrade" class="flex sm:justify-end">
      <PrimaryButton class="w-full sm:w-auto" :icon="['fas', 'clipboard-check']" @click="emit('grade')">
        {{ t('protocol.grade') }}
      </PrimaryButton>
    </div>
  </NeutralContainer>
</template>
