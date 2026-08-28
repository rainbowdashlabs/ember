/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MemberPicker, {type PickableMember} from '@/views/stationview/members/MemberPicker.vue'
import type {StationMember} from '@/api/types'

const modelValue = defineModel<boolean>({required: true})
const memberId = defineModel<string>('memberId', {required: true})

const props = defineProps<{
  members: StationMember[]
}>()

const emit = defineEmits<{
  submit: []
}>()

const {t} = useI18n()

const pickable = computed<PickableMember[]>(() => props.members.map(member => ({
  id: member.id,
  name: member.name || member.email || `#${member.id}`,
  email: member.email,
  identity: member.identity,
  userType: member.userType,
})))

const userTypes = computed(() => [...new Set(props.members.map(member => member.userType).filter(Boolean))] as string[])

const chosen = computed(() => pickable.value.find(member => String(member.id) === memberId.value))
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-3">
      <SubHeader>{{ t('inventory.detail.assign') }}</SubHeader>

      <MemberPicker
          :members="pickable"
          :user-types="userTypes"
          :placeholder="t('inventory.detail.selectMember')"
          @select="memberId = String($event)"
      />

      <div v-if="chosen" class="flex items-center gap-2" data-testid="assign-chosen">
        <MutedText size="sm">{{ t('inventory.detail.assignTo') }}</MutedText>
        <MemberName v-if="chosen.identity" :identity="chosen.identity" class="text-sm font-medium"/>
        <span v-else class="text-sm font-medium">{{ chosen.name }}</span>
      </div>

      <div class="flex justify-end gap-2">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!memberId" @click="emit('submit')">{{ t('inventory.detail.assign') }}</PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
