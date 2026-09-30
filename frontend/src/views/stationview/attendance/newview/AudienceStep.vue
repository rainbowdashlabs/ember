/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import UserTypeCheckboxes from '@/components/input/toggle/UserTypeCheckboxes.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import type {MemberGroup, StationUserTypeName} from '@/api/types'
import type {SessionAudience, TemplateDetail} from '@/api/attendance'
import {templateAudience} from './templateAudience'

/**
 * The step that says whom a new sheet enters, and whose questions it borrows.
 *
 * <p>The two answers add up. Everybody of a chosen type and everybody in a chosen group stands on
 * the sheet, and somebody both of them name stands on it once.
 *
 * <p>Started from a template, the step arrives filled in with that template's user types and groups
 * and may be walked past as it stands, even where the template names nobody. Started without one, it
 * arrives empty and has to be told somebody.
 */
const props = defineProps<{
  templates: TemplateDetail[]
  groups: MemberGroup[]
  /** The template the sheet was started from, whose audience and questions stand ready. */
  template?: TemplateDetail | null
  busy?: boolean
}>()

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'confirm', templateId: number, audience: SessionAudience): void
}>()

const {t} = useI18n()

const prefilled = props.template ? templateAudience(props.template) : null
const chosenTypes = ref<StationUserTypeName[]>(prefilled ? [...prefilled.userTypes] : [])
const chosenGroups = ref<number[]>(prefilled ? [...prefilled.groupIds] : [])

/** A sheet still carries questions, so one template lends its own, the chosen one or else the first. */
const fieldsFrom = ref(String(props.template?.id ?? props.templates[0]?.id ?? ''))

const borrowedTemplate = computed(() => Number(fieldsFrom.value || 0))

const namesNobody = computed(() => chosenTypes.value.length === 0 && chosenGroups.value.length === 0)

const mayConfirm = computed(() => !!borrowedTemplate.value && (!namesNobody.value || !!props.template))

function toggleGroup(groupId: number) {
  chosenGroups.value = chosenGroups.value.includes(groupId)
      ? chosenGroups.value.filter(entry => entry !== groupId)
      : [...chosenGroups.value, groupId]
}

function confirm() {
  if (!mayConfirm.value) return
  emit('confirm', borrowedTemplate.value, {userTypes: chosenTypes.value, groupIds: chosenGroups.value})
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="attendance-audience-step">
    <SubHeader>{{ t('attendanceNew.audienceTitle') }}</SubHeader>
    <MutedText tag="p" size="sm">
      {{ props.template ? t('attendanceNew.audienceFromTemplateHint') : t('attendanceNew.audienceHint') }}
    </MutedText>

    <div class="space-y-2">
      <FieldLabel>{{ t('attendanceNew.userTypes') }}</FieldLabel>
      <UserTypeCheckboxes v-model="chosenTypes" test-id-prefix="attendance-type"/>
    </div>

    <div class="space-y-2">
      <FieldLabel>{{ t('attendanceNew.groups') }}</FieldLabel>
      <MutedText v-if="props.groups.length === 0" tag="p" size="sm">
        {{ t('attendanceNew.noGroups') }}
      </MutedText>
      <div v-else class="flex flex-wrap gap-4">
        <label v-for="group in props.groups" :key="group.id" class="flex items-center gap-2 text-sm">
          <CheckboxInput
              :data-testid="`attendance-group-${group.id}`"
              :model-value="chosenGroups.includes(group.id)"
              @update:model-value="toggleGroup(group.id)"
          />
          <span>{{ group.name }}</span>
        </label>
      </div>
    </div>

    <div class="space-y-2">
      <FieldLabel hint>{{ t('attendanceNew.fieldsFrom') }}</FieldLabel>
      <SelectInput v-model="fieldsFrom" data-testid="attendance-fields-from">
        <option v-for="tpl in props.templates" :key="tpl.id" :value="String(tpl.id)">{{ tpl.name }}</option>
      </SelectInput>
      <MutedText tag="p" size="sm">{{ t('attendanceNew.fieldsFromHint') }}</MutedText>
    </div>

    <ButtonRow pair>
      <SecondaryButton :disabled="props.busy" @click="emit('back')">{{ t('common.back') }}</SecondaryButton>
      <PrimaryButton
          :disabled="props.busy || !mayConfirm"
          data-testid="attendance-audience-confirm"
          @click="confirm"
      >
        {{ t('attendanceNew.create') }}
      </PrimaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>
