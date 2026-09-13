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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {fromMember, userTypesOf} from '@/components/input/select/memberOption'
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

const options = computed(() => props.members.map(fromMember))
const userTypes = computed(() => userTypesOf(options.value))
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-3">
      <SubHeader>{{ t('inventory.detail.assign') }}</SubHeader>

      <MemberSelectInput
          v-model="memberId"
          :members="options"
          :user-types="userTypes"
          :placeholder="t('inventory.detail.selectMember')"
      />

      <div class="flex justify-end gap-2">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!memberId" @click="emit('submit')">{{ t('inventory.detail.assign') }}</PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
