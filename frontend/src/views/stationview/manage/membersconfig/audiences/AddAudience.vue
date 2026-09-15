/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {AssignmentTarget} from '@/api/profileFields'
import type {MemberGroup} from '@/api/types'

/**
 * Puts the question to somebody else.
 *
 * <p>Only what is not already asked is offered, so the same audience cannot be added twice and the
 * control empties itself as the question reaches everybody.
 *
 * <p>Kinds of member and groups are listed under headings of their own. They were one flat list, and
 * a station whose group is called something like a kind of member had no way to tell which of the two
 * a name meant, which matters: a kind of member is one somebody is, a group is one they were put in.
 */
const props = defineProps<{
  roles: readonly string[]
  groups: MemberGroup[]
}>()

const emit = defineEmits<{
  (e: 'add', target: AssignmentTarget): void
}>()

const {t} = useI18n()

/**
 * Reads back what was picked. Roles and groups are numbered apart, so the value carries which of the
 * two it names rather than an id that could mean either.
 */
function onPick(value: string) {
    if (!value) return
    const [kind, name] = value.split(':')
    emit('add', kind === 'GROUP' ? {groupId: Number(name)} : {role: name})
}
</script>

<template>
  <div v-if="props.roles.length > 0 || props.groups.length > 0" class="space-y-1">
    <FieldLabel>{{ t('membersConfig.audiences.add') }}</FieldLabel>
    <SelectInput data-testid="audience-add" model-value="" class="w-full"
                 @update:model-value="onPick(String($event))">
      <option value="">{{ t('membersConfig.audiences.addPlaceholder') }}</option>
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
  </div>
</template>
