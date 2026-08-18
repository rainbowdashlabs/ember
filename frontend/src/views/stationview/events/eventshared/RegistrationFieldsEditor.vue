/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import RegistrationFieldRow from './RegistrationFieldRow.vue'
import {EventFieldTypes, type EventRegistrationFieldDefinition} from '@/api/events'

const fields = defineModel<EventRegistrationFieldDefinition[]>({required: true})

const {t} = useI18n()

const TYPES = [
  {value: EventFieldTypes.STRING, label: t('eventFields.typeString')},
  {value: EventFieldTypes.TEXTAREA, label: t('eventFields.typeTextarea')},
  {value: EventFieldTypes.NUMBER, label: t('eventFields.typeNumber')},
  {value: EventFieldTypes.BOOLEAN, label: t('eventFields.typeBoolean')},
  {value: EventFieldTypes.ENUM, label: t('eventFields.typeEnum')},
  {value: EventFieldTypes.DATE, label: t('eventFields.typeDate')},
  {value: EventFieldTypes.TIME, label: t('eventFields.typeTime')},
  {value: EventFieldTypes.MEMBER, label: t('eventFields.typeMember')},
]

function addField() {
  fields.value = [...fields.value, {
    name: '',
    fieldType: EventFieldTypes.STRING,
    config: {required: false},
    overview: true,
  }]
}

function removeField(index: number) {
  fields.value = fields.value.filter((_, i) => i !== index)
}

function move(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= fields.value.length) return
  const next = [...fields.value]
  const [moved] = next.splice(index, 1)
  if (!moved) return
  next.splice(target, 0, moved)
  fields.value = next
}

function replace(index: number, field: EventRegistrationFieldDefinition) {
  fields.value = fields.value.map((existing, i) => (i === index ? field : existing))
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div>
      <SubHeader>{{ t('events.registrationFields.sectionTitle') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('events.registrationFields.sectionHint') }}</MutedText>
    </div>

    <p v-if="fields.length === 0" class="text-sm text-(--text-muted)">
      {{ t('events.registrationFields.noFields') }}
    </p>

    <RegistrationFieldRow
        v-for="(field, index) in fields"
        :key="index"
        :model-value="field"
        :types="TYPES"
        @update:model-value="f => replace(index, f)"
        @remove="removeField(index)"
        @move="delta => move(index, delta)"
    />

    <SecondaryButton :icon="['fas', 'plus']" @click="addField">
      {{ t('events.registrationFields.addField') }}
    </SecondaryButton>
  </NeutralContainer>
</template>
