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
import TextInput from '@/components/input/text/TextInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import S3BackendForm from '@/components/storage/S3BackendForm.vue'
import SmbBackendForm from '@/components/storage/SmbBackendForm.vue'
import SftpBackendForm from '@/components/storage/SftpBackendForm.vue'
import type {ProbeResult, S3Request, SftpRequest, SmbRequest} from '@/api/storageBackend'

type BackendType = 'LOCAL' | 'CLUSTER' | 'S3' | 'SMB' | 'SFTP'

/**
 * Editor for a storage backend selection, shared by the instance and the station view.
 *
 * All labels are read from `i18nPrefix`, so each caller keeps its own wording. Bind
 * `localRoot` when LOCAL means a writable directory - it then renders the root path
 * field, otherwise LOCAL only shows the caller's `form.localHint` text.
 *
 * `types` says which destinations this caller offers. An association has no instance default to fall back
 * to and no association above it, and a station only sees the association's storage when there is one, so
 * the list is the caller's rather than a constant.
 */
const selectedType = defineModel<BackendType>('selectedType', {required: true})
const s3 = defineModel<S3Request>('s3', {required: true})
const smb = defineModel<SmbRequest>('smb', {required: true})
const sftp = defineModel<SftpRequest>('sftp', {required: true})
const localRoot = defineModel<string>('localRoot')

const props = withDefaults(defineProps<{
    i18nPrefix: string
    probing: boolean
    saving: boolean
    types?: BackendType[]
    showLiveProbe?: boolean
    canProbeLive?: boolean
    probeOutcome?: ProbeResult | null
}>(), {
    types: () => ['LOCAL', 'S3', 'SMB', 'SFTP'],
    showLiveProbe: false,
    canProbeLive: false,
    probeOutcome: null,
})

const emit = defineEmits<{
    (e: 'probe-config'): void
    (e: 'probe-live'): void
    (e: 'apply'): void
}>()

const {t} = useI18n()
</script>

<template>
    <NeutralContainer class="space-y-4">
        <SubHeader>{{ t(`${props.i18nPrefix}.form.title`) }}</SubHeader>
        <MutedText tag="p" size="sm">{{ t(`${props.i18nPrefix}.form.hint`) }}</MutedText>

        <div class="space-y-1">
            <FieldLabel>{{ t(`${props.i18nPrefix}.form.type`) }}</FieldLabel>
            <SelectInput v-model="selectedType" data-testid="storage-backend-type">
                <option v-for="type in props.types" :key="type" :value="type">
                    {{ t(`${props.i18nPrefix}.form.types.${type.toLowerCase()}`) }}
                </option>
            </SelectInput>
        </div>

        <div v-if="selectedType === 'LOCAL' && localRoot !== undefined" class="space-y-1">
            <FieldLabel>{{ t(`${props.i18nPrefix}.form.local.root`) }}</FieldLabel>
            <TextInput v-model="localRoot" placeholder="data"/>
            <MutedText tag="p" size="sm">{{ t(`${props.i18nPrefix}.form.local.hint`) }}</MutedText>
        </div>
        <MutedText v-else-if="selectedType === 'LOCAL'" tag="p" size="sm">
            {{ t(`${props.i18nPrefix}.form.localHint`) }}
        </MutedText>
        <MutedText v-else-if="selectedType === 'CLUSTER'" tag="p" size="sm">
            {{ t(`${props.i18nPrefix}.form.clusterHint`) }}
        </MutedText>
        <S3BackendForm v-else-if="selectedType === 'S3'" v-model="s3"/>
        <SmbBackendForm v-else-if="selectedType === 'SMB'" v-model="smb"/>
        <SftpBackendForm v-else-if="selectedType === 'SFTP'" v-model="sftp"/>

        <div v-if="props.probeOutcome" class="text-sm">
            <Alert v-if="props.probeOutcome.healthy" variant="success">
                {{ t(`${props.i18nPrefix}.probe.ok`) }}
            </Alert>
            <Alert v-else variant="error">
                {{ t(`${props.i18nPrefix}.probe.failed`, {reason: props.probeOutcome.error ?? ''}) }}
            </Alert>
        </div>

        <div class="flex flex-wrap items-center gap-3">
            <SecondaryButton :disabled="props.probing || selectedType === 'LOCAL' || selectedType === 'CLUSTER'"
                             @click="emit('probe-config')">
                {{
                    props.probing
                        ? t(`${props.i18nPrefix}.actions.probing`)
                        : t(`${props.i18nPrefix}.actions.probeConfig`)
                }}
            </SecondaryButton>
            <SecondaryButton
                v-if="props.showLiveProbe"
                :disabled="props.probing || !props.canProbeLive"
                @click="emit('probe-live')">
                {{
                    props.probing
                        ? t(`${props.i18nPrefix}.actions.probing`)
                        : t(`${props.i18nPrefix}.actions.probeLive`)
                }}
            </SecondaryButton>
            <PrimaryButton :disabled="props.saving" @click="emit('apply')">
                {{
                    props.saving
                        ? t(`${props.i18nPrefix}.actions.applying`)
                        : t(`${props.i18nPrefix}.actions.apply`)
                }}
            </PrimaryButton>
        </div>
    </NeutralContainer>
</template>
