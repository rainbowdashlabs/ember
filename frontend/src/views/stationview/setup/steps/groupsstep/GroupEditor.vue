/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ColorInput from '@/components/input/ColorInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PermissionPicker from '@/components/input/PermissionPicker.vue'
import type {MemberGroup, PermissionGrant} from '@/api/types'

defineProps<{
  group: MemberGroup
  color: string
  allRoles: PermissionGrant[]
  permissions: Set<number>
  permissionsLoading: boolean
}>()

const emit = defineEmits<{
  (e: 'colorChange', color: string): void
  (e: 'permissionsChange', ids: Set<number>): void
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <SectionHeader>{{ group.name }}</SectionHeader>

    <div class="space-y-1">
      <FieldLabel>{{ t('memberGroups.color') }}</FieldLabel>
      <div class="flex items-center gap-2">
        <ColorInput :model-value="color" @update:model-value="v => emit('colorChange', v)"/>
        <SecondaryButton v-if="color" compact @click="emit('colorChange', '')">
          <font-awesome-icon :icon="['fas', 'xmark']"/>
        </SecondaryButton>
        <MutedText size="sm">{{ t('memberGroups.colorHint') }}</MutedText>
      </div>
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('setup.steps.groups.permissionsTitle') }}</FieldLabel>
      <Spinner v-if="permissionsLoading" size="md"/>
      <PermissionPicker
          v-else
          :model-value="permissions"
          :all-roles="allRoles"
          @update:model-value="ids => emit('permissionsChange', ids)"
      />
    </div>
  </div>
</template>
