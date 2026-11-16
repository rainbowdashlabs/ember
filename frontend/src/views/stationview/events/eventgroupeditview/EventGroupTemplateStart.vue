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
import SelectInput from '@/components/input/select/SelectInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import TemplateFilesNote from '../eventeditview/TemplateFilesNote.vue'
import type {EventAttachment, EventTemplate} from '@/api/generated/schema'

/**
 * "Aus Vorlage" for a new event group: picking an appointment template fills the form with what an
 * event group can take from it. Fields it cannot take are named, and the template's files are named
 * until the event group is saved and they are attached.
 */
const props = defineProps<{
  templates: EventTemplate[]
  /** The names of the template's fields the event group could not take. */
  leftOut: string[]
  /** The names of the template's fields and questions the form held already, which it kept as they are. */
  present: string[]
  /** The template's files, attached once the event group is saved. */
  files: EventAttachment[]
}>()

const emit = defineEmits<{
  apply: [templateId: number]
}>()

const {t} = useI18n()

function pick(value: unknown) {
  if (value) emit('apply', Number(value))
}
</script>

<template>
  <NeutralContainer v-if="props.templates.length > 0" class="space-y-3" data-testid="event-group-from-template">
    <SectionHeader>{{ t('eventGroups.fromTemplate') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('eventGroups.fromTemplateHint') }}</MutedText>
    <SelectInput model-value="" class="w-full sm:w-64" data-testid="event-group-apply-template" @update:model-value="pick">
      <option value="" disabled>{{ t('eventTemplates.loadTemplate') }}</option>
      <option v-for="template in props.templates" :key="template.id" :value="String(template.id)">{{ template.name }}</option>
    </SelectInput>
    <Alert v-if="props.leftOut.length > 0" variant="info" data-testid="event-group-template-left-out">
      {{ t('eventGroups.fromTemplateLeftOut', {fields: props.leftOut.join(', ')}) }}
    </Alert>
    <Alert v-if="props.present.length > 0" variant="info" data-testid="event-group-template-present">
      {{ t('eventGroups.fromTemplatePresent', {names: props.present.join(', ')}) }}
    </Alert>
  </NeutralContainer>
  <TemplateFilesNote v-if="props.files.length > 0" :files="props.files" :hint="t('eventGroups.fromTemplateFiles')"/>
</template>
