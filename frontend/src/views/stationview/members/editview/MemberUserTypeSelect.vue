/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import BulletList from '@/components/typography/BulletList.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {stationMembers} from '@/api'
import {StationUserType, StationUserTypeLabels} from '@/api/types'
import type {MemberGroup, StationUserType as StationUserTypeName} from '@/api/generated/schema'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * A member's type, with what changing it does to their groups said before it happens.
 *
 * <p>A group bound to member types takes only those, so a member who becomes another type leaves the
 * bound groups that do not take the new one. The groups that would be left are asked for first; where
 * there are any, the change waits for a confirmation listing them.
 */
const props = defineProps<{
  memberId: number
  userType: string
}>()

const emit = defineEmits<{
  changed: [userType: string, leftGroups: MemberGroup[]]
  failed: [failure: Failure]
}>()

const {t} = useI18n()

const pending = ref<string | null>(null)
const wouldLeave = ref<MemberGroup[]>([])
const saving = ref(false)

const options = [
  StationUserType.MANAGER,
  StationUserType.TEAM,
  StationUserType.GUARDIAN,
  StationUserType.MEMBER,
  StationUserType.TRIAL,
]

async function ask(value: string) {
  if (value === props.userType) return
  try {
    const groups = await stationMembers.getUserTypeConsequences(props.memberId, value)
    if (groups.length === 0) {
      await apply(value)
      return
    }
    wouldLeave.value = groups
    pending.value = value
  } catch (e) {
    emit('failed', describeFailure(e, t))
  }
}

async function apply(value: string) {
  saving.value = true
  try {
    const change = await stationMembers.setUserType(props.memberId, value)
    pending.value = null
    emit('changed', value, change.leftGroups)
  } catch (e) {
    emit('failed', describeFailure(e, t))
  } finally {
    saving.value = false
  }
}

function label(type: string): string {
  return StationUserTypeLabels[type as StationUserTypeName] ?? type
}
</script>

<template>
  <SelectInput :model-value="userType" class="max-w-xs" @update:model-value="v => { if (v) ask(String(v)) }">
    <option v-for="type in options" :key="type" :value="type">{{ label(type) }}</option>
  </SelectInput>

  <Modal :model-value="pending !== null" @update:model-value="open => { if (!open) pending = null }">
    <div class="space-y-4">
      <SectionHeader>{{ t('memberEdit.userTypeConfirmTitle') }}</SectionHeader>
      <p class="text-sm">{{ t('memberEdit.userTypeConfirmText', {type: label(pending ?? '')}) }}</p>
      <BulletList>
        <li v-for="group in wouldLeave" :key="group.id">{{ group.name }}</li>
      </BulletList>
      <ButtonRow pair align="end">
        <SecondaryButton @click="pending = null">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving" @click="pending && apply(pending)">
          {{ saving ? t('common.loading') : t('memberEdit.userTypeConfirm') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
