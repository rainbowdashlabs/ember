/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type { MemberCheckState } from '@/api/inventoryCheck'

defineProps<{
  state: MemberCheckState
  uncheckedCount: number
  checkMode: boolean
}>()

defineEmits<{
  startCheckMode: []
  markAllConfirmed: []
  cancel: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
    <div>
      <SubHeader><MemberName :identity="state.memberIdentity ?? null"/></SubHeader>
      <p class="text-sm text-(--text-muted)">{{ t('inventory.check.title') }}</p>
    </div>
    <div class="flex gap-2">
      <PrimaryButton :icon="['fas', 'list-check']" v-if="uncheckedCount > 0 && !checkMode" class="text-sm" @click="$emit('startCheckMode')">
        {{ t('inventory.check.rapidCheck') }}
      </PrimaryButton>
      <SecondaryButton v-if="state.assigned.length > 0 && !checkMode" class="text-sm" @click="$emit('markAllConfirmed')">
        {{ t('inventory.check.markAll') }}
      </SecondaryButton>
      <SecondaryButton @click="$emit('cancel')">{{ t('inventory.check.cancel') }}</SecondaryButton>
    </div>
  </div>
</template>
