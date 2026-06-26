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

const {t} = useI18n()

const name = defineModel<string>('name', {required: true})
const description = defineModel<string>('description', {required: true})
const dueAt = defineModel<string>('dueAt', {required: true})
const isPublic = defineModel<boolean>('isPublic', {required: true})
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
