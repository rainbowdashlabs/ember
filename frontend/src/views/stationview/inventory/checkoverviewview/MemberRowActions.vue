/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {EnrichedCheckSummary} from '@/api/generated/schema'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'

defineProps<{
  member: EnrichedCheckSummary
  lockedByMe: boolean
  lockedByOther: boolean
}>()

const emit = defineEmits<{
  (e: 'view-last-check', member: EnrichedCheckSummary): void
  (e: 'start-check', memberId: number): void
}>()

const {t} = useI18n()
</script>

<template>
  <ButtonRow align="end">
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
  </ButtonRow>
</template>
