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
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const name = defineModel<string>('name', {required: true})
const description = defineModel<string>('description', {required: true})
const trainingEnabled = defineModel<boolean>('trainingEnabled', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('quiz.import.newCatalogTitle') }}</SubHeader>
    <TextInput v-model="name" :placeholder="t('quiz.catalogs.name')" />
    <TextAreaInput v-model="description" :placeholder="t('quiz.catalogs.description')" />
    <FieldLabel inline>
      <ToggleInput v-model="trainingEnabled" />
      {{ t('quiz.catalogs.trainingEnabled') }}
    </FieldLabel>
  </NeutralContainer>
</template>
