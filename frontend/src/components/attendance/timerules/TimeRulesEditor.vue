/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DragList from '@/components/input/DragList.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {MemberGroup, MemberWithName, UserTag} from '@/api/generated/schema'
import {moveWithin} from '@/util/reorder'
import {emptyDraft, minutesFit, namesSomebody, type TimeRuleDraft} from './timeRuleDraft'

/**
 * The preparation and follow-up rules of a template or a sheet, edited as one list.
 *
 * <p>Each rule names whom it credits with the same picker every restricted feature uses, and how many
 * minutes before and after the appointment. A rule that lost a group or tag it named comes back naming
 * nobody and says so, because it can only be saved once its people are named again. The list holds
 * no saving of its own: the template and the sheet write it where they keep it.
 */
const rules = defineModel<TimeRuleDraft[]>({required: true})

defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
  members: MemberWithName[]
}>()

const {t} = useI18n()

function add() {
  rules.value = [...rules.value, emptyDraft()]
}

function remove(index: number) {
  rules.value = rules.value.filter((_, at) => at !== index)
}

function reorder(from: number, to: number) {
  rules.value = moveWithin(rules.value, from, to)
}
</script>

<template>
  <div class="space-y-3" data-testid="time-rules-editor">
    <MutedText v-if="rules.length === 0" tag="p" size="sm">{{ t('attendanceTimeRules.empty') }}</MutedText>

    <DragList
        v-else
        :items="rules"
        :key-fn="(rule) => rule.key"
        class="space-y-3"
        @reorder="reorder"
    >
      <template #default="{item: rule, index}">
        <div class="space-y-3 rounded-lg border border-(--border) p-3" data-testid="time-rule">
          <Alert v-if="!rule.intact && !namesSomebody(rule)" variant="error">{{ t('attendanceTimeRules.lostPart') }}</Alert>
          <div class="flex items-start gap-2">
            <LabelledField :label="t('attendanceTimeRules.label')" hint class="flex-1">
              <TextInput
                  v-model="rule.label"
                  :placeholder="t('attendanceTimeRules.labelPlaceholder')"
              />
            </LabelledField>
            <IconButton
                :icon="['fas', 'trash']"
                :label="t('attendanceTimeRules.remove')"
                class="mt-6"
                @click="remove(index)"
            />
          </div>
          <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <LabelledField
                :label="t('attendanceTimeRules.preparation')"
                :error="minutesFit(rule.preparationMinutes) ? undefined : t('attendanceTimeRules.minutesRange')"
            >
              <NumberInput v-model="rule.preparationMinutes"/>
            </LabelledField>
            <LabelledField
                :label="t('attendanceTimeRules.followUp')"
                :error="minutesFit(rule.followUpMinutes) ? undefined : t('attendanceTimeRules.minutesRange')"
            >
              <NumberInput v-model="rule.followUpMinutes"/>
            </LabelledField>
          </div>
          <LabelledField
              :label="t('attendanceTimeRules.audience')"
              :error="namesSomebody(rule) ? undefined : t('attendanceTimeRules.namesNobody')"
          >
            <RestrictionPicker
                v-model="rule.audience"
                :groups="groups"
                :tags="tags"
                :members="members"
                show-members
            />
          </LabelledField>
        </div>
      </template>
    </DragList>

    <SecondaryButton :icon="['fas', 'plus']" data-testid="time-rule-add" @click="add">
      {{ t('attendanceTimeRules.add') }}
    </SecondaryButton>
  </div>
</template>
