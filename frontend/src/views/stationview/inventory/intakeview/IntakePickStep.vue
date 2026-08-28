/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {StationUserType, StationUserTypeLabels} from '@/api/types'
import type {MemberGroup} from '@/api/types'
import {IntakeAudience, type IntakeAudienceName} from './intakeAudience'

/**
 * Who the table opens with.
 *
 * <p>A stock-taking starts from the member list rather than from the shelf, so the first thing asked
 * is which members.
 */
const audience = defineModel<IntakeAudienceName>('audience', {required: true})
const userType = defineModel<string>('userType', {required: true})
const groupId = defineModel<string>('groupId', {required: true})

defineProps<{
  groups: MemberGroup[]
}>()

const {t} = useI18n()

const userTypes = computed(() => Object.values(StationUserType))

function typeLabel(value: string): string {
  return StationUserTypeLabels[value as keyof typeof StationUserTypeLabels] ?? value
}
</script>

<template>
  <div class="space-y-3">
    <MutedText size="sm" tag="p">{{ t('inventory.intake.whoHint') }}</MutedText>
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.intake.who') }}</FieldLabel>
        <SelectInput v-model="audience" class="w-full" data-testid="intake-audience">
          <option :value="IntakeAudience.ALL">{{ t('inventory.intake.whoAll') }}</option>
          <option :value="IntakeAudience.USER_TYPE">{{ t('inventory.intake.whoUserType') }}</option>
          <option :value="IntakeAudience.GROUP">{{ t('inventory.intake.whoGroup') }}</option>
        </SelectInput>
      </div>

      <div v-if="audience === IntakeAudience.USER_TYPE" class="space-y-1">
        <FieldLabel>{{ t('inventory.intake.userType') }}</FieldLabel>
        <SelectInput v-model="userType" class="w-full" data-testid="intake-user-type">
          <option v-for="value in userTypes" :key="value" :value="value">{{ typeLabel(value) }}</option>
        </SelectInput>
      </div>

      <div v-else-if="audience === IntakeAudience.GROUP" class="space-y-1">
        <FieldLabel>{{ t('inventory.intake.group') }}</FieldLabel>
        <SelectInput v-model="groupId" class="w-full" data-testid="intake-group">
          <option value="">{{ t('inventory.intake.chooseGroup') }}</option>
          <option v-for="group in groups" :key="group.id" :value="String(group.id)">{{ group.name }}</option>
        </SelectInput>
      </div>
    </div>
  </div>
</template>
