/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SingleSelectDropdown from '@/components/input/select/SingleSelectDropdown.vue'
import FieldValueDisplay from '@/components/display/FieldValueDisplay.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { ProfileField } from '@/api/profileFields'
import type { StationMember } from '@/api/types'
import MutedText from '@/components/typography/MutedText.vue'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  managers: StationMember[]
  availableManagers: StationMember[]
  managerValues: Map<number, Map<number, string>>
  managerRoles: Map<number, string[]>
  fields: ProfileField[]
  readonly?: boolean
  memberDisplayNameFn: (m: StationMember) => string
  getManagerFieldsFn: (mgrId: number) => ProfileField[]
  getManagerFieldValueFn: (mgrId: number, fieldId: number) => unknown
}>(), {
  readonly: false,
})

const emit = defineEmits<{
  linkManager: [managerId: number]
  removeManager: [managerId: number]
  createManager: [data: { firstName: string; lastName: string; email: string }]
  editManager: [managerId: number]
}>()

const showLinkManager = ref(false)
const selectedManagerId = ref('')

const managerOptions = computed(() =>
    props.availableManagers.map(m => ({
      value: String(m.id),
      label: props.memberDisplayNameFn(m),
    }))
)

const showCreateManager = ref(false)
const newMgrFirstName = ref('')
const newMgrLastName = ref('')
const newMgrEmail = ref('')

function doLinkManager() {
  if (!selectedManagerId.value) return
  emit('linkManager', Number(selectedManagerId.value))
  showLinkManager.value = false
  selectedManagerId.value = ''
}

function doCreateManager() {
  if (!newMgrFirstName.value || !newMgrLastName.value || !newMgrEmail.value) return
  emit('createManager', {
    firstName: newMgrFirstName.value,
    lastName: newMgrLastName.value,
    email: newMgrEmail.value,
  })
  showCreateManager.value = false
  newMgrFirstName.value = ''
  newMgrLastName.value = ''
  newMgrEmail.value = ''
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <SubHeader class="text-sm">{{ t('memberDetail.managers') }}</SubHeader>
      <div v-if="!readonly" class="flex items-center gap-2">
        <SecondaryButton :icon="['fas', 'link']" @click="showLinkManager = !showLinkManager">
          {{ t('memberDetail.linkManager') }}
        </SecondaryButton>
        <SecondaryButton :icon="['fas', 'plus']" @click="showCreateManager = !showCreateManager">
          {{ t('memberDetail.createManager') }}
        </SecondaryButton>
      </div>
    </div>

    <MutedText tag="div" size="sm" class="py-2" v-if="managers.length === 0">
      {{ t('memberDetail.noManagers') }}
    </MutedText>

    <div class="space-y-3">
      <div v-for="mgr in managers" :key="mgr.id" data-testid="guardian-row" class="rounded-lg px-4 py-3 bg-bg-light-accent/30 dark:bg-bg-dark-accent/30 space-y-2">
        <div class="flex items-center justify-between">
          <div>
            <span class="font-semibold">{{ memberDisplayNameFn(mgr) }}</span>
            <MutedText class="ml-2" v-if="mgr.email">{{ mgr.email }}</MutedText>
          </div>
          <div v-if="!readonly" class="flex items-center gap-2">
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
    <div v-if="!readonly && showLinkManager" class="space-y-2 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
      <FieldLabel>{{ t('memberDetail.selectManager') }}</FieldLabel>
      <div class="flex gap-2">
        <SingleSelectDropdown
            :options="managerOptions"
            :model-value="selectedManagerId"
            :placeholder="t('memberDetail.selectManagerPlaceholder')"
            searchable
            clearable
            @update:model-value="selectedManagerId = $event"
        />
        <PrimaryButton :disabled="!selectedManagerId" @click="doLinkManager">
          {{ t('memberDetail.assign') }}
        </PrimaryButton>
      </div>
    </div>

    <!-- Create new manager -->
    <div v-if="!readonly && showCreateManager" class="space-y-3 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
      <FieldLabel>{{ t('memberDetail.createManagerTitle') }}</FieldLabel>
      <div class="grid gap-3 sm:grid-cols-3">
        <TextInput v-model="newMgrFirstName" :placeholder="t('memberDetail.firstName')" />
        <TextInput v-model="newMgrLastName" :placeholder="t('memberDetail.lastName')" />
        <TextInput v-model="newMgrEmail" :placeholder="t('memberDetail.email')" />
      </div>
      <SecondaryButton :icon="['fas', 'plus']" :disabled="!newMgrFirstName || !newMgrLastName || !newMgrEmail" @click="doCreateManager">
        {{ t('memberDetail.createManagerSubmit') }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
