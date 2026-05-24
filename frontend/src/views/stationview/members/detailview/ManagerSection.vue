/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldValueDisplay from '@/components/display/FieldValueDisplay.vue'
import type { ProfileField, StationMember } from '@/api/types'

const { t } = useI18n()

const props = defineProps<{
  managers: StationMember[]
  availableManagers: StationMember[]
  managerValues: Map<number, Map<number, string>>
  managerRoles: Map<number, string[]>
  fields: ProfileField[]
  memberDisplayNameFn: (m: StationMember) => string
  getManagerFieldsFn: (mgrId: number) => ProfileField[]
  getManagerFieldValueFn: (mgrId: number, fieldId: number) => unknown
}>()

const emit = defineEmits<{
  linkManager: [managerId: number]
  removeManager: [managerId: number]
  createManager: [data: { firstName: string; lastName: string; email: string }]
  editManager: [managerId: number]
}>()

const showLinkManager = ref(false)
const selectedManagerId = ref('')

const showCreateManager = ref(false)
const newMgrFirstName = ref('')
const newMgrLastName = ref('')
const newMgrEmail = ref('')
const creatingManager = ref(false)

function doLinkManager() {
  if (!selectedManagerId.value) return
  emit('linkManager', Number(selectedManagerId.value))
  showLinkManager.value = false
  selectedManagerId.value = ''
}

async function doCreateManager() {
  if (!newMgrFirstName.value || !newMgrLastName.value || !newMgrEmail.value) return
  creatingManager.value = true
  emit('createManager', {
    firstName: newMgrFirstName.value,
    lastName: newMgrLastName.value,
    email: newMgrEmail.value,
  })
  creatingManager.value = false
  showCreateManager.value = false
  newMgrFirstName.value = ''
  newMgrLastName.value = ''
  newMgrEmail.value = ''
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold">{{ t('memberDetail.managers') }}</h3>
      <div class="flex items-center gap-2">
        <SecondaryButton @click="showLinkManager = !showLinkManager">
          <font-awesome-icon :icon="['fas', 'link']" class="mr-1" />
          {{ t('memberDetail.linkManager') }}
        </SecondaryButton>
        <SecondaryButton @click="showCreateManager = !showCreateManager">
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
          {{ t('memberDetail.createManager') }}
        </SecondaryButton>
      </div>
    </div>

    <div v-if="managers.length === 0" class="text-(--text-muted) text-sm py-2">
      {{ t('memberDetail.noManagers') }}
    </div>

    <div class="space-y-3">
      <div v-for="mgr in managers" :key="mgr.id" class="rounded-lg px-4 py-3 bg-bg-light-accent/30 dark:bg-bg-dark-accent/30 space-y-2">
        <div class="flex items-center justify-between">
          <div>
            <span class="font-semibold">{{ memberDisplayNameFn(mgr) }}</span>
            <span v-if="mgr.email" class="ml-2 text-xs text-(--text-muted)">{{ mgr.email }}</span>
          </div>
          <div class="flex items-center gap-2">
            <EditButton @click="emit('editManager', mgr.id)" />
            <DeleteButton @click="emit('removeManager', mgr.id)" />
          </div>
        </div>
        <div v-if="getManagerFieldsFn(mgr.id).length > 0" class="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          <div v-for="field in getManagerFieldsFn(mgr.id)" :key="field.id" class="text-sm">
            <span class="text-(--text-muted)">{{ field.name }}:</span>
            <span class="ml-1 font-medium"><FieldValueDisplay :value="getManagerFieldValueFn(mgr.id, field.id)" :field-type="field.fieldType"/></span>
          </div>
        </div>
      </div>
    </div>

    <!-- Link existing manager -->
    <div v-if="showLinkManager" class="space-y-2 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
      <label class="block text-sm font-medium">{{ t('memberDetail.selectManager') }}</label>
      <div class="flex gap-2">
        <SelectInput v-model="selectedManagerId" class="flex-1">
          <option value="" disabled>{{ t('memberDetail.selectManagerPlaceholder') }}</option>
          <option v-for="m in availableManagers" :key="m.id" :value="String(m.id)">{{ memberDisplayNameFn(m) }}</option>
        </SelectInput>
        <PrimaryButton :disabled="!selectedManagerId" @click="doLinkManager">
          {{ t('memberDetail.assign') }}
        </PrimaryButton>
      </div>
    </div>

    <!-- Create new manager -->
    <div v-if="showCreateManager" class="space-y-3 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
      <label class="block text-sm font-medium">{{ t('memberDetail.createManagerTitle') }}</label>
      <div class="grid gap-3 sm:grid-cols-3">
        <TextInput v-model="newMgrFirstName" :placeholder="t('memberDetail.firstName')" />
        <TextInput v-model="newMgrLastName" :placeholder="t('memberDetail.lastName')" />
        <TextInput v-model="newMgrEmail" :placeholder="t('memberDetail.email')" />
      </div>
      <SecondaryButton :disabled="!newMgrFirstName || !newMgrLastName || !newMgrEmail || creatingManager" @click="doCreateManager">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ creatingManager ? t('common.loading') : t('memberDetail.createManagerSubmit') }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
