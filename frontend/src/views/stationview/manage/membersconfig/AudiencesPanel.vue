/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import AudienceRow from './audiences/AudienceRow.vue'
import AddAudience from './audiences/AddAudience.vue'
import type {EditableField, AssignmentTarget} from '@/api/profileFields'
import type {AssignmentRequest, FieldSet, MemberGroup} from '@/api/generated/schema'
import type {Audience} from '@/composables/useFieldsConfig'

/**
 * Who the selected question is put to, and how it is put to each of them.
 *
 * <p>The other half of the model. One question stands on as many forms as it is assigned to, and what
 * differs between them lives here: where it sits, how wide it is drawn, whether that audience must
 * answer it and whether they may write to it at all.
 */
const props = defineProps<{
  field: EditableField | null
  audiences: Audience[]
  unaskedRoles: readonly string[]
  unaskedGroups: MemberGroup[]
  /**
   * The group the question belongs to, with its questions. A group is asked as one: adding or removing an
   * audience here does so for every question of it.
   */
  fieldSet?: {set: FieldSet; members: EditableField[]} | null
}>()

const memberNames = computed(() => props.fieldSet?.members.map(member => member.name).join(', ') ?? '')

const emit = defineEmits<{
  add: [target: AssignmentTarget]
  remove: [target: AssignmentTarget]
  set: [audience: Audience, patch: Partial<AssignmentRequest>]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="audiences-panel">
    <SectionHeader>{{ t('membersConfig.audiences.title') }}</SectionHeader>

    <EmptyState v-if="!field" compact>{{ t('membersConfig.audiences.selectAQuestion') }}</EmptyState>

    <template v-else>
      <InfoContainer v-if="fieldSet" class="space-y-1 text-sm" data-testid="audiences-for-set">
        <p class="flex items-center gap-2 font-medium">
          <font-awesome-icon :icon="['fas', 'layer-group']" class="h-3.5 w-3.5"/>
          {{ t('membersConfig.audiences.setTitle', {name: fieldSet.set.name}) }}
        </p>
        <p>{{ t('membersConfig.audiences.setHint', {names: memberNames}) }}</p>
      </InfoContainer>
      <MutedText v-else tag="p" size="sm">{{ t('membersConfig.audiences.hint', {name: field.name}) }}</MutedText>

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
