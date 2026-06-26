/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PermissionPicker from '@/components/input/PermissionPicker.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()

const dummyRoles = [
  {id: 1, permission: 'ATTENDANCE_MANAGEMENT', label: 'Anwesenheitsverwaltung'},
  {id: 2, permission: 'INVENTORY_READ', label: 'Inventar lesen'},
]
const dummySelected = new Set([1])
</script>

<template>
  <NeutralContainer class="space-y-2">
    <FieldLabel class="text-(--text-muted)">{{ t('memberGroups.tabPermissions') }}</FieldLabel>
    <PermissionPicker :model-value="dummySelected" :all-roles="dummyRoles"/>
    <MutedText size="sm">{{ t('helpCenter.membersGroups.permissionsHint') }}</MutedText>
  </NeutralContainer>
</template>
