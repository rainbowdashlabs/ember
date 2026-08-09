/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {MemberCheckSummary} from '@/api/inventoryCheck'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'

const props = defineProps<{
  member: MemberCheckSummary
  lockedByMe: boolean
  lockedByOther: boolean
}>()

const emit = defineEmits<{
  (e: 'view-last-check', member: MemberCheckSummary): void
  (e: 'start-check', memberId: number): void
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center justify-end gap-1">
    <MutedIconButton
        v-if="member.lastCheckedAt"
        :icon="['fas', 'eye']"
        :label="t('inventory.check.showLastCheck')"
        @click="emit('view-last-check', member)"
    />
    <SecondaryButton v-if="lockedByMe" @click="emit('start-check', member.memberId)">
      {{ t('inventory.check.continue') }}
    </SecondaryButton>
    <PrimaryButton v-else :disabled="lockedByOther"
                   @click="emit('start-check', member.memberId)">
      {{ t('inventory.check.start') }}
    </PrimaryButton>
  </div>
</template>
