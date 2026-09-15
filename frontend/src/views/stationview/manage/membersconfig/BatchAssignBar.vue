/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {AssignmentTarget} from '@/api/profileFields'
import type {MemberGroup} from '@/api/types'

/**
 * Putting several questions to one audience at once.
 *
 * <p>Only here while something is ticked, so the panel is its usual self until there is a reason for
 * it not to be. Every audience is offered rather than only the unasked ones: a batch spans questions
 * that are already put to different people, and which of them this changes is not one answer.
 */
const props = defineProps<{
  count: number
  roles: readonly string[]
  groups: MemberGroup[]
}>()

const emit = defineEmits<{
  (e: 'assign', target: AssignmentTarget): void
  (e: 'clear'): void
}>()

const {t} = useI18n()

function onPick(value: string) {
    if (!value) return
    const [kind, name] = value.split(':')
    emit('assign', kind === 'GROUP' ? {groupId: Number(name)} : {role: name})
}
</script>

<template>
  <div
      data-testid="batch-assign"
      class="flex flex-wrap items-center gap-3 rounded-theme border border-primary/40 bg-primary/5 px-3 py-2">
    <span class="text-sm font-medium">{{ t('membersConfig.batch.chosen', {count: props.count}) }}</span>

    <SelectInput data-testid="batch-assign-target" model-value="" class="min-w-56"
                 @update:model-value="onPick(String($event))">
      <option value="">{{ t('membersConfig.batch.assignPlaceholder') }}</option>
      <optgroup v-if="props.roles.length > 0" :label="t('membersConfig.audiences.kindRoles')">
        <option v-for="role in props.roles" :key="role" :value="`ROLE:${role}`">
          {{ t(`membersConfig.roles.${role}`) }}
        </option>
      </optgroup>
      <optgroup v-if="props.groups.length > 0" :label="t('membersConfig.audiences.kindGroups')">
        <option v-for="group in props.groups" :key="group.id" :value="`GROUP:${group.id}`">
          {{ group.name }}
        </option>
      </optgroup>
    </SelectInput>

    <SecondaryButton class="ml-auto" @click="emit('clear')">{{ t('membersConfig.batch.clear') }}</SecondaryButton>
  </div>
</template>
