/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import type {ManagedMember} from '@/api/managedMembers'

/**
 * Claiming an item, for oneself or for somebody in one's care.
 *
 * A parent collecting a glove for their child is the ordinary case in a youth station, and the
 * picker is what makes the entry say whose it is: without it every claim reads as the parent's own.
 */
const visible = defineModel<boolean>({required: true})

defineProps<{
  managed: ManagedMember[]
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'confirm', memberId: number | null): void
}>()

const {t} = useI18n()
const forMemberId = ref<number | null>(null)

watch(visible, value => {
  if (value) forMemberId.value = null
})
</script>

<template>
  <Modal v-model="visible">
    <div class="space-y-4 p-4">
      <SubHeader>{{ t('lostAndFound.claimConfirmTitle') }}</SubHeader>
      <p class="text-sm text-(--text-muted)">{{ t('lostAndFound.claimConfirmMessage') }}</p>

      <div v-if="managed.length" class="space-y-1">
        <FieldLabel>{{ t('lostAndFound.claimFor') }}</FieldLabel>
        <SelectInput v-model="forMemberId" class="w-full" data-testid="claim-for">
          <option :value="null">{{ t('lostAndFound.claimForMyself') }}</option>
          <option v-for="member in managed" :key="member.id" :value="member.id">{{ member.name }}</option>
        </SelectInput>
      </div>

      <div class="flex justify-end gap-2">
        <SecondaryButton @click="visible = false">{{ t('common.cancel') }}</SecondaryButton>
        <SuccessButton :disabled="loading" @click="emit('confirm', forMemberId)">
          {{ loading ? t('common.loading') : t('lostAndFound.claim') }}
        </SuccessButton>
      </div>
    </div>
  </Modal>
</template>
