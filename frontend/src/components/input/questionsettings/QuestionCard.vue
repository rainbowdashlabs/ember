/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import FieldTypePicker from '@/components/input/FieldTypePicker.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import QuestionSettingsEditor from './QuestionSettingsEditor.vue'
import type {QuestionSetting, QuestionSettingsModel} from './questionSettings'
import type {FieldType, MemberGroup, UserTag} from '@/api/generated/schema'
import type {MemberOption} from '@/components/input/select/memberOption'

/**
 * One question of a form, edited where it stands.
 *
 * <p>Every feature that asks something has the same first row, the name and the type with a button
 * to take the question away, and the same settings under it: the answers of a choice, whom a member
 * field may name, the bounds of a number and what the form starts from. The attendance sheet and the
 * waiting list wrote all of that into a dialog of their own, which opened over the list and asked
 * for a position by number, while an appointment wrote it in place. This is the row and the settings
 * once, and what a feature asks on top of them goes in the slot under them.
 *
 * <p>The name, the type and the shared settings are models of their own rather than one record,
 * because every feature stores the three differently and reads them into the shared settings shape
 * only for editing.
 */
const name = defineModel<string>('name', {required: true})
const fieldType = defineModel<FieldType>('fieldType', {required: true})
const settings = defineModel<QuestionSettingsModel>('settings', {required: true})

defineProps<{
  /** The types this feature offers, in the order it offers them. */
  types: readonly FieldType[]
  /** The shared settings this feature offers; a type they mean nothing for leaves them out by itself. */
  offers: readonly QuestionSetting[]
  nameLabel: string
  namePlaceholder?: string
  /** Whether the line saying what the chosen type is for is shown under the type. */
  describe?: boolean
  groups?: MemberGroup[]
  tags?: UserTag[]
  /** Whom a starting value may name, where the feature offers one for a member field. */
  members?: MemberOption[]
  /** A line under the starting value, where a feature says when it is used. */
  defaultHint?: string
}>()

const emit = defineEmits<{
  remove: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="rounded border border-(--border) p-3 space-y-3">
    <div class="grid grid-cols-1 sm:grid-cols-[1fr_auto_auto] gap-2 items-end">
      <LabelledField :label="nameLabel">
        <TextInput :model-value="name" :placeholder="namePlaceholder" data-testid="question-name"
                   @update:model-value="value => name = String(value ?? '')"/>
      </LabelledField>
      <LabelledField :label="t('fieldTypes.type')" class="sm:w-56">
        <FieldTypePicker :model-value="fieldType" :types="types" :describe="describe"
                         data-testid="question-type" @update:model-value="type => fieldType = type"/>
      </LabelledField>
      <DeleteButton class="justify-self-end" :label="t('common.delete')" @click="emit('remove')"/>
    </div>

    <QuestionSettingsEditor
        v-model="settings"
        :default-hint="defaultHint"
        :field-type="fieldType"
        :groups="groups"
        :members="members"
        :offers="offers"
        :tags="tags"
    />

    <slot/>
  </div>
</template>
