/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import S3BackendForm from '@/components/storage/S3BackendForm.vue'
import SmbBackendForm from '@/components/storage/SmbBackendForm.vue'
import SftpBackendForm from '@/components/storage/SftpBackendForm.vue'
import type {S3Request, SftpRequest, SmbRequest} from '@/api/storageBackend'

type BackendType = 'LOCAL' | 'S3' | 'SMB' | 'SFTP'

defineProps<{
  probingConfig: boolean
  saving: boolean
}>()

const emit = defineEmits<{
  probe: []
  apply: []
}>()

const selectedType = defineModel<BackendType>('selectedType', {required: true})
const localRoot = defineModel<string>('localRoot', {required: true})
const s3 = defineModel<S3Request>('s3', {required: true})
const smb = defineModel<SmbRequest>('smb', {required: true})
const sftp = defineModel<SftpRequest>('sftp', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('adminStorageBackend.form.title') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('adminStorageBackend.form.hint') }}</MutedText>

    <div class="space-y-1">
      <FieldLabel>{{ t('adminStorageBackend.form.type') }}</FieldLabel>
      <SelectInput v-model="selectedType">
        <option value="LOCAL">{{ t('adminStorageBackend.form.types.local') }}</option>
        <option value="S3">{{ t('adminStorageBackend.form.types.s3') }}</option>
        <option value="SMB">{{ t('adminStorageBackend.form.types.smb') }}</option>
        <option value="SFTP">{{ t('adminStorageBackend.form.types.sftp') }}</option>
      </SelectInput>
    </div>

    <div v-if="selectedType === 'LOCAL'" class="space-y-1">
      <FieldLabel>{{ t('adminStorageBackend.form.local.root') }}</FieldLabel>
      <TextInput v-model="localRoot" placeholder="data"/>
      <MutedText tag="p" size="sm">{{ t('adminStorageBackend.form.local.hint') }}</MutedText>
    </div>
    <S3BackendForm v-else-if="selectedType === 'S3'" v-model="s3"/>
    <SmbBackendForm v-else-if="selectedType === 'SMB'" v-model="smb"/>
    <SftpBackendForm v-else-if="selectedType === 'SFTP'" v-model="sftp"/>

    <div class="flex flex-wrap items-center gap-3">
      <SecondaryButton :disabled="probingConfig || selectedType === 'LOCAL'" @click="emit('probe')">
        {{ probingConfig ? t('adminStorageBackend.actions.probing') : t('adminStorageBackend.actions.probeConfig') }}
      </SecondaryButton>
      <PrimaryButton :disabled="saving" @click="emit('apply')">
        {{ saving ? t('adminStorageBackend.actions.applying') : t('adminStorageBackend.actions.apply') }}
      </PrimaryButton>
    </div>
  </NeutralContainer>
</template>
