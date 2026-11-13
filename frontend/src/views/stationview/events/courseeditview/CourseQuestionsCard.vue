/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import RegistrationFieldsEditor from '../eventshared/RegistrationFieldsEditor.vue'
import type {RegistrationFieldDefinition} from '@/api/generated/schema'

/** The questions a course asks once of everybody signing up, for all its parts. */
const questions = defineModel<RegistrationFieldDefinition[]>({required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>{{ t('courses.questions') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('courses.questionsHint') }}</MutedText>
    <RegistrationFieldsEditor v-model="questions"/>
  </NeutralContainer>
</template>
