/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type {RequiredTemplate} from '@/api/generated/schema'

/**
 * The documents an appointment or an appointment template asks participants to bring: document
 * templates marked for appointments. Every registered participant gets a copy filled with their data
 * and the appointment's. A template archived since it was chosen stays until it is taken off, but
 * cannot be chosen anew.
 */
const chosen = defineModel<RequiredTemplate[]>({required: true})

const props = defineProps<{
  /** The templates for appointments the station offers. */
  offered: RequiredTemplate[]
}>()

const {t} = useI18n()

const adding = ref<number | null>(null)
const addable = computed(() => props.offered.filter(template =>
    !chosen.value.some(taken => taken.templateId === template.templateId)))

function add(value: string | number | null | undefined) {
  const template = addable.value.find(candidate => candidate.templateId === Number(value))
  if (template) chosen.value = [...chosen.value, template]
  adding.value = null
}

function remove(templateId: number) {
  chosen.value = chosen.value.filter(template => template.templateId !== templateId)
}
</script>

<template>
  <NeutralContainer v-if="offered.length > 0 || chosen.length > 0" class="space-y-3" data-testid="document-requirements">
    <SubHeader>{{ t('events.documents.title') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('events.documents.hint') }}</MutedText>
    <ul v-if="chosen.length > 0" class="space-y-2">
      <li v-for="template in chosen" :key="template.templateId" class="flex items-center gap-2" data-testid="document-requirement">
        <span class="flex-1">{{ template.name }}</span>
        <SecondaryBadge v-if="template.archived">{{ t('events.documents.archived') }}</SecondaryBadge>
        <IconButton :icon="['fas', 'xmark']" :label="t('events.documents.remove')" @click="remove(template.templateId)"/>
      </li>
    </ul>
    <LabelledField v-if="addable.length > 0" :label="t('events.documents.add')">
      <SelectInput :model-value="adding" data-testid="document-requirement-add" @update:model-value="add">
        <option :value="null" disabled>{{ t('events.documents.choose') }}</option>
        <option v-for="template in addable" :key="template.templateId" :value="template.templateId">{{ template.name }}</option>
      </SelectInput>
    </LabelledField>
  </NeutralContainer>
</template>
