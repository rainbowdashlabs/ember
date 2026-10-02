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
import DragList from '@/components/input/DragList.vue'
import {moveWithin} from '@/util/reorder'
import EventQuestionEditor from './EventQuestionEditor.vue'
import {blankQuestion} from './eventQuestions'
import type {MemberLike} from '@/components/input/select/memberOption'
import type {RegistrationFieldDefinition} from '@/api/generated/schema'

/**
 * The questions every registrant of an appointment, or of the appointments made from a template, is
 * asked. Each is written in the one question editor every appointment question uses.
 */
const fields = defineModel<RegistrationFieldDefinition[]>({required: true})

defineProps<{
  /** Whom a question naming a member may start from. */
  allMembers?: MemberLike[]
}>()

const {t} = useI18n()

function addField() {
  const {name, fieldType, config} = blankQuestion()
  fields.value = [...fields.value, {name, fieldType, config, overview: true}]
}

function removeField(index: number) {
  fields.value = fields.value.filter((_, i) => i !== index)
}

function move(fromIndex: number, toIndex: number) {
  fields.value = moveWithin(fields.value, fromIndex, toIndex)
}

function replace(index: number, field: RegistrationFieldDefinition) {
  fields.value = fields.value.map((existing, i) => (i === index ? field : existing))
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="registration-question-list">
    <div>
      <SubHeader>{{ t('events.registrationFields.sectionTitle') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('events.registrationFields.sectionHint') }}</MutedText>
    </div>

    <p v-if="fields.length === 0" class="text-sm text-(--text-muted)">
      {{ t('events.registrationFields.noFields') }}
    </p>

    <DragList :items="fields" :key-fn="(_, index) => index" @reorder="move">
      <template #default="{index}">
        <EventQuestionEditor
            mode="registrant"
            :all-members="allMembers"
            :model-value="fields[index]!"
            @update:model-value="f => replace(index, f)"
            @remove="removeField(index)"
        />
      </template>
    </DragList>

    <SecondaryButton :icon="['fas', 'plus']" @click="addField">
      {{ t('events.registrationFields.addField') }}
    </SecondaryButton>
  </NeutralContainer>
</template>
