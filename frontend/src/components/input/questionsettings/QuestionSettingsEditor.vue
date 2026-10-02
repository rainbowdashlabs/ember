/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import QuestionOptionsEditor from '@/components/input/QuestionOptionsEditor.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {MemberOption} from '@/components/input/select/memberOption'
import QuestionBoundsEditor from './QuestionBoundsEditor.vue'
import QuestionDefaultEditor from './QuestionDefaultEditor.vue'
import MemberConstraintEditor from './MemberConstraintEditor.vue'
import {holdsValue, memberConstraintOf} from '@/api/fieldTypes'
import {FieldType, type MemberGroup, type UserTag} from '@/api/generated/schema'
import type {QuestionSetting, QuestionSettingsModel} from './questionSettings'

/**
 * The settings of a field, whatever feature it belongs to, as far as they are shared.
 *
 * <p>Seven settings screens each wrote the answers of a choice, the starting value, the bounds of a
 * number and the group a member field is narrowed to in their own way. Here a feature says which of
 * these it offers and gets them drawn the one way, for the types they mean something for. What only
 * the feature has, such as a lane, a unit or a width, stays on the feature's own screen beside this.
 */
const settings = defineModel<QuestionSettingsModel>({required: true})

const props = defineProps<{
  fieldType: string
  /** The settings this feature offers; a type they mean nothing for leaves them out by itself. */
  offers: readonly QuestionSetting[]
  groups?: MemberGroup[]
  tags?: UserTag[]
  /** Whom a starting value may name, where the feature offers one for a member field. */
  members?: MemberOption[]
  /** A line under the starting value, where a feature says when it is used. */
  defaultHint?: string
}>()

const {t} = useI18n()

function offered(setting: QuestionSetting): boolean {
  return props.offers.includes(setting)
}

const showsOptions = computed(() => offered('options') && props.fieldType === FieldType.CHOICE)
const showsMembers = computed(() => offered('members') && memberConstraintOf(props.fieldType) !== null)
const showsBounds = computed(() =>
    (offered('bounds') || offered('step')) && props.fieldType === FieldType.NUMBER)
const showsDefault = computed(() => offered('default') && holdsValue(props.fieldType))

function update(patch: Partial<QuestionSettingsModel>) {
  settings.value = {...settings.value, ...patch}
}
</script>

<template>
  <div class="space-y-4">
    <QuestionOptionsEditor
        v-if="showsOptions"
        :label="t('questionSettings.options')"
        :model-value="settings.options ?? []"
        @update:model-value="options => update({options})"
    />

    <MemberConstraintEditor
        v-if="showsMembers"
        :field-type="fieldType"
        :group-id="settings.groupId"
        :groups="groups"
        :tag-id="settings.tagId"
        :tags="tags"
        :user-type="settings.userType"
        @update:group-id="groupId => update({groupId})"
        @update:tag-id="tagId => update({tagId})"
        @update:user-type="userType => update({userType})"
    />

    <QuestionBoundsEditor
        v-if="showsBounds"
        :max="settings.max"
        :min="settings.min"
        :step="settings.step"
        :with-step="offered('step')"
        @update:max="max => update({max})"
        @update:min="min => update({min})"
        @update:step="step => update({step})"
    />

    <QuestionDefaultEditor
        v-if="showsDefault"
        :field-type="fieldType"
        :hint="defaultHint"
        :max="settings.max"
        :members="members"
        :min="settings.min"
        :model-value="settings.defaultValue ?? null"
        :options="settings.options ?? []"
        :step="settings.step"
        :today-default="offered('todayDefault')"
        @update:model-value="defaultValue => update({defaultValue})"
    />

    <label v-if="offered('required')" class="flex items-center gap-2 text-sm">
      <ToggleInput :model-value="settings.required ?? false" data-testid="question-required"
                   @update:model-value="required => update({required})"/>
      {{ t('questionSettings.required') }}
    </label>
  </div>
</template>
