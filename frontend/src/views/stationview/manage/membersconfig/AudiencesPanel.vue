/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import AudienceRow from './audiences/AudienceRow.vue'
import AddAudience from './audiences/AddAudience.vue'
import type {ProfileField, AssignmentRequest, AssignmentTarget} from '@/api/profileFields'
import type {Audience} from '@/composables/useFieldsConfig'
import type {MemberGroup} from '@/api/types'

/**
 * Who the selected question is put to, and how it is put to each of them.
 *
 * <p>The other half of the model. One question stands on as many forms as it is assigned to, and what
 * differs between them lives here: where it sits, how wide it is drawn, whether that audience must
 * answer it and whether they may write to it at all.
 */
defineProps<{
  field: ProfileField | null
  audiences: Audience[]
  unaskedRoles: readonly string[]
  unaskedGroups: MemberGroup[]
}>()

const emit = defineEmits<{
  (e: 'add', target: AssignmentTarget): void
  (e: 'remove', target: AssignmentTarget): void
  (e: 'set', audience: Audience, patch: Partial<AssignmentRequest>): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="audiences-panel">
    <SectionHeader>{{ t('membersConfig.audiences.title') }}</SectionHeader>

    <EmptyState v-if="!field" compact>{{ t('membersConfig.audiences.selectAQuestion') }}</EmptyState>

    <template v-else>
      <MutedText tag="p" size="sm">{{ t('membersConfig.audiences.hint', {name: field.name}) }}</MutedText>

      <EmptyState v-if="audiences.length === 0" compact>
        {{ t('membersConfig.audiences.noneYet') }}
      </EmptyState>

      <AudienceRow
          v-for="audience in audiences"
          :key="audience.label"
          :audience="audience"
          :field="field"
          @remove="emit('remove', audience.target)"
          @set="(patch: Partial<AssignmentRequest>) => emit('set', audience, patch)"/>

      <AddAudience
          :roles="unaskedRoles"
          :groups="unaskedGroups"
          @add="(target: AssignmentTarget) => emit('add', target)"/>
    </template>
  </NeutralContainer>
</template>
