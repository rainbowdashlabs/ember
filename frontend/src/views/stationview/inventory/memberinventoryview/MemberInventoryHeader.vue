/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {StationMember} from '@/api/types'

defineProps<{
  member: StationMember | null
}>()

defineEmits<{
  (e: 'back'): void
}>()

const {t} = useI18n()

function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}
</script>

<template>
  <div class="flex items-center justify-between flex-wrap gap-2">
    <SubHeader>
      {{ member ? `${t('profile.inventory')} — ${memberDisplayName(member)}` : t('profile.inventory') }}
    </SubHeader>
    <SecondaryButton :icon="['fas', 'chevron-left']" @click="$emit('back')">
      {{ t('common.back') }}
    </SecondaryButton>
  </div>
</template>
