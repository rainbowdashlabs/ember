/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import FormLabel from '@/components/input/FormLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {CLUSTER_FIELD_SCOPES, CLUSTER_FIELD_TYPES} from '@/api/clusterFields'
import type {ClusterField, ClusterFieldRequest} from '@/api/clusterFields'

const props = defineProps<{
  field: ClusterField | null
  busy: boolean
}>()

const emit = defineEmits<{
  save: [request: ClusterFieldRequest]
  cancel: []
}>()

const {t} = useI18n()

const name = ref('')
const fieldType = ref<string>('TEXT')
const scope = ref<string>('MEMBER')
const position = ref<number | undefined>(0)
const stationReadonly = ref(true)
const keepOnArchive = ref(false)
const required = ref(false)
const notifyOnChange = ref(false)

watch(() => props.field, field => {
  name.value = field?.name ?? ''
  fieldType.value = field?.fieldType ?? 'TEXT'
  scope.value = field?.scope ?? 'MEMBER'
  position.value = field?.position ?? 0
  stationReadonly.value = field?.stationReadonly ?? true
  keepOnArchive.value = field?.keepOnArchive ?? false
  required.value = field?.config?.required ?? false
  notifyOnChange.value = field?.config?.notifyOnChange ?? false
}, {immediate: true})

function save() {
  if (!name.value.trim()) return
  emit('save', {
    name: name.value.trim(),
    fieldType: fieldType.value,
    scope: scope.value,
    position: position.value ?? 0,
    stationReadonly: stationReadonly.value,
    keepOnArchive: keepOnArchive.value,
    config: {required: required.value, notifyOnChange: notifyOnChange.value},
  })
}
</script>

<template>
  <div class="space-y-4">
    <div class="space-y-1">
      <FormLabel>{{ t('clusterFields.nameLabel') }}</FormLabel>
      <TextInput v-model="name" :placeholder="t('clusterFields.namePlaceholder')"/>
    </div>

    <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
      <div class="space-y-1">
        <FormLabel>{{ t('clusterFields.typeLabel') }}</FormLabel>
        <SelectInput v-model="fieldType">
          <option v-for="type in CLUSTER_FIELD_TYPES" :key="type" :value="type">
            {{ t(`clusterFields.types.${type}`) }}
          </option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <FormLabel>{{ t('clusterFields.scopeLabel') }}</FormLabel>
        <SelectInput v-model="scope">
          <option v-for="entry in CLUSTER_FIELD_SCOPES" :key="entry" :value="entry">
            {{ t(`clusterFields.scopes.${entry}`) }}
          </option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <FormLabel>{{ t('clusterFields.positionLabel') }}</FormLabel>
        <NumberInput v-model="position"/>
      </div>
    </div>

    <div class="space-y-2">
      <label class="flex items-center gap-2 text-sm">
        <CheckboxInput v-model="stationReadonly"/>
        <span>{{ t('clusterFields.stationReadonlyLabel') }}</span>
      </label>
      <label class="flex items-center gap-2 text-sm">
        <CheckboxInput v-model="required"/>
        <span>{{ t('clusterFields.requiredLabel') }}</span>
      </label>
      <label class="flex items-center gap-2 text-sm">
        <CheckboxInput v-model="notifyOnChange"/>
        <span>{{ t('clusterFields.notifyLabel') }}</span>
      </label>
      <label class="flex items-center gap-2 text-sm">
        <CheckboxInput v-model="keepOnArchive"/>
        <span>{{ t('clusterFields.keepOnArchiveLabel') }}</span>
      </label>
    </div>

    <div class="flex items-center gap-2">
      <PrimaryButton :disabled="busy || !name.trim()" @click="save">{{ t('common.save') }}</PrimaryButton>
      <SecondaryButton :disabled="busy" @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
    </div>
  </div>
</template>
