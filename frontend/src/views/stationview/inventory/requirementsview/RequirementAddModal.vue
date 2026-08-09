/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { Inventory } from '@/api/inventory'
import type { MemberGroup } from '@/api/types'
import RequirementAddForm from './RequirementAddForm.vue'

const show = defineModel<boolean>('show', { default: false })
const targetType = defineModel<'userType' | 'group'>('targetType', { default: 'userType' })
const userType = defineModel<string>('userType', { default: '' })
const groupId = defineModel<string>('groupId', { default: '' })
const inventoryId = defineModel<string>('inventoryId', { default: '' })
const quantity = defineModel<number>('quantity', { default: 1 })

defineProps<{
  inventories: Inventory[]
  allGroups: MemberGroup[]
  saving: boolean
}>()

const emit = defineEmits<{
  submit: []
}>()

const { t } = useI18n()
</script>

<template>
  <Modal v-model="show">
    <div class="space-y-4">
      <SubHeader>{{ t('inventory.requirements.add') }}</SubHeader>

      <RequirementAddForm
        v-model:target-type="targetType"
        v-model:user-type="userType"
        v-model:group-id="groupId"
        v-model:inventory-id="inventoryId"
        v-model:quantity="quantity"
        :inventories="inventories"
        :all-groups="allGroups"
      />

      <div class="flex justify-end gap-3">
        <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton
          :disabled="saving || !inventoryId || (targetType === 'userType' && !userType) || (targetType === 'group' && !groupId)"
          @click="emit('submit')"
        >
          {{ saving ? t('common.loading') : t('common.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
