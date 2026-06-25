/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {MemberGroup} from '@/api/types'

defineProps<{
  availableGroups: MemberGroup[]
}>()

const groupId = defineModel<string>({required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('attendanceConfig.fieldGroup') }}</FieldLabel>
    <SelectInput v-model="groupId">
      <option disabled value="">{{ t('attendanceConfig.fieldGroupPlaceholder') }}</option>
      <option v-for="group in availableGroups" :key="group.id" :value="String(group.id)">{{ group.name }}</option>
    </SelectInput>
  </div>
</template>
