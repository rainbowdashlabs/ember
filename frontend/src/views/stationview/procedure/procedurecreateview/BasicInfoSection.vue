/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import type {MemberOption} from '@/components/input/select/memberOption'

const {t} = useI18n()

const name = defineModel<string>('name', {required: true})
const description = defineModel<string>('description', {required: true})
const dueAt = defineModel<string>('dueAt', {required: true})
const isPublic = defineModel<boolean>('isPublic', {required: true})
/** The member the procedure is about, as the picker holds it: the id, or empty for nobody in particular. */
const about = defineModel<string>('about', {required: true})

defineProps<{
  /** The members a procedure can be about. */
  members: MemberOption[]
  /** Whether the procedure is about somebody already, who can be changed but not taken away. */
  aboutFixed?: boolean
  /** Whether the procedure has to name somebody, as every new one does. */
  aboutRequired?: boolean
}>()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('procedures.details') }}</SubHeader>
    <div>
      <FieldLabel class="mb-1">{{ t('procedures.name') }}</FieldLabel>
      <TextInput v-model="name" :placeholder="t('procedures.name')" required/>
    </div>
    <div>
      <FieldLabel class="mb-1">{{ t('procedures.description') }}</FieldLabel>
      <TextAreaInput v-model="description" :placeholder="t('procedures.description')"/>
    </div>
    <div>
      <FieldLabel class="mb-1">{{ t('procedures.about') }}</FieldLabel>
      <MemberSelectInput v-model="about" :members="members" :clearable="!aboutFixed && !aboutRequired"
                         :empty-label="t('procedures.aboutNobody')"
                         data-testid="procedure-about"/>
      <p class="text-xs text-[var(--text-muted)] mt-1">{{ t('procedures.aboutHint') }}</p>
    </div>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <div>
        <FieldLabel class="mb-1">{{ t('procedures.dueDate') }}</FieldLabel>
        <DateInput v-model="dueAt"/>
      </div>
      <div class="flex items-center gap-2 mt-4">
        <ToggleInput v-model="isPublic"/>
        <FieldLabel>{{ t('procedures.publicProcedure') }}</FieldLabel>
      </div>
    </div>
  </NeutralContainer>
</template>
