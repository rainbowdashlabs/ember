/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import S3BackendForm from '@/components/storage/S3BackendForm.vue'
import SmbBackendForm from '@/components/storage/SmbBackendForm.vue'
import SftpBackendForm from '@/components/storage/SftpBackendForm.vue'
import type {ProbeResult, S3Request, SftpRequest, SmbRequest} from '@/api/storageBackend'

type BackendType = 'LOCAL' | 'S3' | 'SMB' | 'SFTP'

const selectedType = defineModel<BackendType>('selectedType', {required: true})
const s3 = defineModel<S3Request>('s3', {required: true})
const smb = defineModel<SmbRequest>('smb', {required: true})
const sftp = defineModel<SftpRequest>('sftp', {required: true})

const props = defineProps<{
  probing: boolean
  saving: boolean
  hasOverride: boolean
  probeOutcome: ProbeResult | null
}>()

const emit = defineEmits<{
  (e: 'probe-config'): void
  (e: 'probe-live'): void
  (e: 'apply'): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('stationStorageBackend.form.title') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('stationStorageBackend.form.hint') }}</MutedText>

    <div class="space-y-1">
      <FieldLabel>{{ t('stationStorageBackend.form.type') }}</FieldLabel>
      <SelectInput v-model="selectedType">
        <option value="LOCAL">{{ t('stationStorageBackend.form.types.local') }}</option>
        <option value="S3">{{ t('stationStorageBackend.form.types.s3') }}</option>
        <option value="SMB">{{ t('stationStorageBackend.form.types.smb') }}</option>
        <option value="SFTP">{{ t('stationStorageBackend.form.types.sftp') }}</option>
      </SelectInput>
    </div>

    <MutedText v-if="selectedType === 'LOCAL'" tag="p" size="sm">
      {{ t('stationStorageBackend.form.localHint') }}
    </MutedText>
    <S3BackendForm v-else-if="selectedType === 'S3'" v-model="s3"/>
    <SmbBackendForm v-else-if="selectedType === 'SMB'" v-model="smb"/>
    <SftpBackendForm v-else-if="selectedType === 'SFTP'" v-model="sftp"/>

    <div v-if="props.probeOutcome" class="text-sm">
      <Alert v-if="props.probeOutcome.healthy" variant="success">
        {{ t('stationStorageBackend.probe.ok') }}
      </Alert>
      <Alert v-else variant="error">
        {{ t('stationStorageBackend.probe.failed', {reason: props.probeOutcome.error ?? ''}) }}
      </Alert>
    </div>

    <div class="flex flex-wrap items-center gap-3">
      <SecondaryButton :disabled="props.probing || selectedType === 'LOCAL'" @click="emit('probe-config')">
        {{
          props.probing ? t('stationStorageBackend.actions.probing') : t('stationStorageBackend.actions.probeConfig')
        }}
      </SecondaryButton>
      <SecondaryButton :disabled="props.probing || !props.hasOverride" @click="emit('probe-live')">
        {{ props.probing ? t('stationStorageBackend.actions.probing') : t('stationStorageBackend.actions.probeLive') }}
      </SecondaryButton>
      <PrimaryButton :disabled="props.saving" @click="emit('apply')">
        {{ props.saving ? t('stationStorageBackend.actions.applying') : t('stationStorageBackend.actions.apply') }}
      </PrimaryButton>
    </div>
  </NeutralContainer>
</template>
