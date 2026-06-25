/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {StationMember} from '@/api/types'

const show = defineModel<boolean>({default: false})

const props = defineProps<{
  members: StationMember[]
}>()

const emit = defineEmits<{
  assign: [memberId: number]
}>()

const {t} = useI18n()

const memberId = ref('')

watch(show, (visible) => {
  if (visible) memberId.value = ''
})

function submit() {
  if (!memberId.value) return
  emit('assign', Number(memberId.value))
}
</script>

<template>
  <Modal v-model="show">
    <div class="space-y-4">
      <SectionHeader>{{ t('itemDetail.assignTitle') }}</SectionHeader>
      <SelectInput v-model="memberId">
        <option value="">{{ t('itemDetail.selectMember') }}</option>
        <option v-for="m in props.members" :key="m.id" :value="String(m.id)">
          {{ m.name || m.email }}
        </option>
      </SelectInput>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!memberId" @click="submit">{{ t('itemDetail.assign') }}</PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
