/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ProfileFieldsLayout, {type LaidOutField} from '@/components/profilefields/ProfileFieldsLayout.vue'
import type {ProfileField} from '@/api/profileFields'

/**
 * The questions a new member is asked, drawn the way they are drawn everywhere else.
 *
 * <p>This step used to lay them out itself, in two lists of its own making, one of the questions
 * that must be answered and one of the rest. Two things followed from that. A heading the station
 * had put between its questions is not a question, and a step that knew nothing of headings drew
 * each of them as an empty box to type in. And the order the station arranged its questions in was
 * lost, twice over, since sorting them by whether they must be answered pulls them apart.
 */
const props = defineProps<{
  fields: ProfileField[]
  values: Map<number, string>
}>()

const emit = defineEmits<{
  next: []
  back: []
  setValue: [fieldId: number, value: string]
}>()

const {t} = useI18n()

function valueOf(field: LaidOutField): string {
  return props.values.get(field.id) ?? ''
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('membersCreate.stepFields') }}</SectionHeader>

    <EmptyState compact v-if="fields.length === 0">{{ t('membersCreate.noFields') }}</EmptyState>

    <ProfileFieldsLayout
        v-else
        :fields="fields"
        :get-value="valueOf"
        can-edit-readonly
        @update="(field, value) => emit('setValue', field.id, value)"
    />

    <ButtonRow pair align="between">
      <SecondaryButton @click="emit('back')">{{ t('membersCreate.back') }}</SecondaryButton>
      <PrimaryButton @click="emit('next')">{{ t('membersCreate.next') }}</PrimaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>
