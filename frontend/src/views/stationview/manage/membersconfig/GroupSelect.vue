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

/** Which group the fields of group scope are being configured for. */
defineProps<{ groups: MemberGroup[] }>()

const model = defineModel<string>({required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-2">
    <FieldLabel>{{ t('membersConfig.selectGroup') }}</FieldLabel>
    <SelectInput v-model="model" class="w-full">
      <option disabled value="">{{ t('membersConfig.selectGroupPlaceholder') }}</option>
      <option v-for="group in groups" :key="group.id" :value="String(group.id)">{{ group.name }}</option>
    </SelectInput>
  </div>
</template>
