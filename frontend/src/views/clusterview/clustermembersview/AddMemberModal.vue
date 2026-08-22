/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FormLabel from '@/components/input/FormLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {ClusterUserType} from '@/api/clusters'

/** Giving somebody a job at the association, which is what makes them a member of it. */
const props = defineProps<{
  modelValue: boolean
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'add', email: string, userType: string): void
}>()

const {t} = useI18n()

// Written out rather than through the shared proxy: this modal also emits an event carrying two
// strings, and the proxy's generic then reads the wrong signature off the emit.
const open = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})
const email = ref('')
const userType = ref<string>(ClusterUserType.CLUSTER_USER)

function submit() {
  if (!email.value.trim()) return
  emit('add', email.value.trim(), userType.value)
  email.value = ''
}
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SubHeader>{{ t('clusterMembers.addTitle') }}</SubHeader>
      <p class="text-sm text-(--text-muted)">{{ t('clusterMembers.addHint') }}</p>

      <div class="space-y-1">
        <FormLabel>{{ t('clusterMembers.emailLabel') }}</FormLabel>
        <TextInput v-model="email" :placeholder="t('clusterMembers.emailPlaceholder')"/>
      </div>

      <div class="space-y-1">
        <FormLabel>{{ t('clusterMembers.userTypeLabel') }}</FormLabel>
        <SelectInput v-model="userType">
          <option :value="ClusterUserType.CLUSTER_USER">{{ t('clusterOverview.role.CLUSTER_USER') }}</option>
          <option :value="ClusterUserType.CLUSTER_ADMIN">{{ t('clusterOverview.role.CLUSTER_ADMIN') }}</option>
        </SelectInput>
      </div>

      <div class="flex justify-end gap-3">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="props.saving || !email.trim()" @click="submit">
          {{ t('common.add') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
