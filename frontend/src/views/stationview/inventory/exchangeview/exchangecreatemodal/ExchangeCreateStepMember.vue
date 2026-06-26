/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type { StationMember } from '@/api/types'
import type { ManagedMember } from '@/api/managedMembers'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const { canManageInventory, isGuardian, sessionInfo } = useSession()

const memberId = defineModel<string>({ required: true })

defineProps<{
  membersWithItems: Set<number>
  membersWithItemsList: StationMember[]
  managedWithItemsList: ManagedMember[]
  managed: ManagedMember[]
}>()

const emit = defineEmits<{
  next: []
  cancel: []
}>()
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('exchanges.member') }}</FieldLabel>
    <SelectInput v-if="canManageInventory()" v-model="memberId">
      <option value="" disabled>{{ t('exchanges.selectMember') }}</option>
      <option v-for="m in membersWithItemsList" :key="m.id" :value="String(m.id)">
        {{ m.name || m.email || `#${m.id}` }}
      </option>
    </SelectInput>
    <SelectInput v-else-if="isGuardian() && managed.length > 0" v-model="memberId">
      <option v-if="membersWithItems.has(sessionInfo?.member?.id ?? 0)" :value="String(sessionInfo?.member?.id ?? '')">{{ t('profile.myInventorySelf') }}</option>
      <option v-for="m in managedWithItemsList" :key="m.id" :value="String(m.id)">
        {{ m.name || m.email }}
      </option>
    </SelectInput>
  </div>
  <div class="flex justify-end gap-3">
    <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
    <PrimaryButton :disabled="!memberId" @click="emit('next')">
      {{ t('exchanges.stepNext') }}
    </PrimaryButton>
  </div>
</template>
