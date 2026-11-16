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

/** The questions an event group asks once of everybody signing up, for all its parts. */
const questions = defineModel<RegistrationFieldDefinition[]>({required: true})

defineProps<{
  /** Inside the event group's form: explanations behind hint icons, no heading where the control names itself. */
  compact?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <RegistrationFieldsEditor v-if="compact" v-model="questions" :hint="t('eventGroups.questionsHint')" compact/>
  <NeutralContainer v-else class="space-y-3">
    <SectionHeader>{{ t('eventGroups.questions') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('eventGroups.questionsHint') }}</MutedText>
    <RegistrationFieldsEditor v-model="questions"/>
  </NeutralContainer>
</template>
