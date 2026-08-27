/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {StationUserTypeLabels} from '@/api/types'

/**
 * Narrowing the people a group or a tag can still be given.
 *
 * <p>The same bar above both pickers, because both list every member of the station and the one being
 * looked for is known by name or by what kind of member they are. The kinds offered are the kinds
 * actually left to add, so choosing one never empties the list on its own.
 */
defineProps<{
  /** The kinds present among the people still on offer. Empty where the list holds no members at all. */
  userTypes: string[]
}>()

const search = defineModel<string>('search', {required: true})
const userType = defineModel<string>('userType', {required: true})

const {t} = useI18n()

function typeLabel(value: string): string {
  return StationUserTypeLabels[value as keyof typeof StationUserTypeLabels] ?? value
}
</script>

<template>
  <div class="flex flex-wrap items-center gap-2">
    <TextInput
        v-model="search"
        data-testid="picker-search"
        :placeholder="t('memberGroups.searchMembers')"
        class="flex-1 min-w-40"
    />
    <SelectInput v-if="userTypes.length > 1" v-model="userType" data-testid="picker-user-type" class="w-44">
      <option value="">{{ t('memberGroups.anyUserType') }}</option>
      <option v-for="value in userTypes" :key="value" :value="value">{{ typeLabel(value) }}</option>
    </SelectInput>
  </div>
</template>
