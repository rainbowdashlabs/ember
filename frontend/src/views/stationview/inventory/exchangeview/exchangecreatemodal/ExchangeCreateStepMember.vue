/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import { fromMember, userTypesOf, type MemberOption } from '@/components/input/select/memberOption'
import type { StationMember } from '@/api/types'
import type { ManagedMember } from '@/api/managedMembers'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const { canManageInventory, isGuardian, sessionInfo } = useSession()

const memberId = defineModel<string>({ required: true })

const props = defineProps<{
  membersWithItems: Set<number>
  membersWithItemsList: StationMember[]
  managedWithItemsList: ManagedMember[]
  managed: ManagedMember[]
}>()

const emit = defineEmits<{
  next: []
  cancel: []
}>()

const showsWholeStation = computed(() => canManageInventory())
const showsHousehold = computed(() => !showsWholeStation.value && isGuardian() && props.managed.length > 0)

/**
 * Whose gear can be exchanged.
 *
 * <p>Whoever looks after the inventory picks out of the station. A guardian picks out of their own
 * household, which includes themselves when they are holding something.
 */
const options = computed<MemberOption[]>(() => {
  if (showsWholeStation.value) return props.membersWithItemsList.map(fromMember)
  const ownId = sessionInfo.value?.member?.id
  const self: MemberOption[] = ownId && props.membersWithItems.has(ownId)
      ? [{value: String(ownId), name: t('profile.myInventorySelf')}]
      : []
  return [...self, ...props.managedWithItemsList.map(fromMember)]
})

const userTypes = computed(() => (showsWholeStation.value ? userTypesOf(options.value) : []))
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('exchanges.member') }}</FieldLabel>
    <MemberSelectInput
        v-if="showsWholeStation || showsHousehold"
        v-model="memberId"
        :members="options"
        :user-types="userTypes"
        :placeholder="t('exchanges.selectMember')"
    />
  </div>
  <div class="flex justify-end gap-3">
    <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
    <PrimaryButton :disabled="!memberId" @click="emit('next')">
      {{ t('exchanges.stepNext') }}
    </PrimaryButton>
  </div>
</template>
