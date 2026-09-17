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
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import {StationUserType, StationUserTypeLabels, type StationUserTypeName} from '@/api/types'
import type {SessionAudience, TemplateDetail} from '@/api/attendance'
import type {MemberGroup} from '@/api/types'

/**
 * The second step of starting a sheet nobody kept a template for: whom to enter, and whose questions
 * the sheet borrows.
 *
 * <p>The two answers add up. Everybody of a chosen type and everybody in a chosen group stands on
 * the sheet, and somebody both of them name stands on it once.
 */
const props = defineProps<{
  templates: TemplateDetail[]
  groups: MemberGroup[]
  busy?: boolean
}>()

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'confirm', templateId: number, audience: SessionAudience): void
}>()

const {t} = useI18n()

const chosenTypes = ref<StationUserTypeName[]>([])
const chosenGroups = ref<number[]>([])

/** A sheet still carries questions, so one template lends its own, and the first stands ready. */
const fieldsFrom = ref(String(props.templates[0]?.id ?? ''))

const offeredTypes = Object.values(StationUserType)

const borrowedTemplate = computed(() => Number(fieldsFrom.value || 0))

const namesNobody = computed(() => chosenTypes.value.length === 0 && chosenGroups.value.length === 0)

function toggle<T>(list: T[], value: T): T[] {
  return list.includes(value) ? list.filter(entry => entry !== value) : [...list, value]
}

function confirm() {
  if (namesNobody.value || !borrowedTemplate.value) return
  emit('confirm', borrowedTemplate.value, {userTypes: chosenTypes.value, groupIds: chosenGroups.value})
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="attendance-audience-step">
    <SubHeader>{{ t('attendanceNew.audienceTitle') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('attendanceNew.audienceHint') }}</MutedText>

    <div class="space-y-2">
      <FieldLabel>{{ t('attendanceNew.userTypes') }}</FieldLabel>
      <div class="flex flex-wrap gap-4">
        <label v-for="type in offeredTypes" :key="type" class="flex items-center gap-2 text-sm">
          <CheckboxInput
              :data-testid="`attendance-type-${type}`"
              :model-value="chosenTypes.includes(type)"
              @update:model-value="chosenTypes = toggle(chosenTypes, type)"
          />
          <span>{{ StationUserTypeLabels[type] }}</span>
        </label>
      </div>
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
              @update:model-value="chosenGroups = toggle(chosenGroups, group.id)"
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
          :disabled="props.busy || namesNobody || !borrowedTemplate"
          data-testid="attendance-audience-confirm"
          @click="confirm"
      >
        {{ t('attendanceNew.create') }}
      </PrimaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>
