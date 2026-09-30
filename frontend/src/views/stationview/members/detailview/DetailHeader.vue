/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import { useSession } from '@/composables/useSession'

const props = defineProps<{
  memberId: number
  canEdit: boolean
  formerSuccess: boolean
  deleteSuccess: boolean
}>()

const emit = defineEmits<{
  (e: 'open-former-modal'): void
  (e: 'open-delete-modal'): void
}>()

const { t } = useI18n()
const router = useRouter()
const { canManageMembers } = useSession()
</script>

<template>
  <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
    <ButtonRow>
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="router.push({ name: 'members-list' })">
        {{ t('memberDetail.back') }}
      </SecondaryButton>
    </ButtonRow>
    <ButtonRow v-if="canEdit" align="end">
      <ErrorButton v-if="canManageMembers() && !formerSuccess && !deleteSuccess" :icon="['fas', 'user-slash']" @click="emit('open-former-modal')">
        {{ t('memberDetail.markFormer') }}
      </ErrorButton>
      <ErrorButton v-if="canManageMembers() && !deleteSuccess && !formerSuccess" :icon="['fas', 'trash']" @click="emit('open-delete-modal')">
        {{ t('memberDetail.deleteData') }}
      </ErrorButton>
      <PrimaryButton :icon="['fas', 'pen']" @click="router.push({ name: 'members-edit', params: { id: props.memberId } })">
        {{ t('memberDetail.edit') }}
      </PrimaryButton>
    </ButtonRow>
  </div>
</template>
