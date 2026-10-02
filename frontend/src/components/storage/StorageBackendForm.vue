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
import ButtonRow from '@/components/button/ButtonRow.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import S3BackendForm from '@/components/storage/S3BackendForm.vue'
import SmbBackendForm from '@/components/storage/SmbBackendForm.vue'
import SftpBackendForm from '@/components/storage/SftpBackendForm.vue'
import StorageProbeOutcome from '@/components/storage/StorageProbeOutcome.vue'
import type {ProbeResult} from '@/api/generated/schema'
import type {StorageBackendChoice} from '@/composables/useStorageBackendEditor'
import type {S3Form, SftpForm, SmbForm} from '@/util/storageBackendForm'

/**
 * Editor for a storage backend selection, shared by the station's, the association's and the instance's
 * screen.
 *
 * All labels are read from `i18nPrefix`, so each caller keeps its own wording. Bind
 * `localRoot` when LOCAL means a writable directory - it then renders the root path
 * field, otherwise LOCAL only shows the caller's `form.localHint` text.
 *
 * `types` says which destinations this caller offers. An association has no instance default to fall back
 * to and no association above it, and a station only sees the association's storage when there is one, so
 * the list is the caller's rather than a constant. The storage already saved is tested from the summary
 * card; this form tests what is typed into it.
 */
const selectedType = defineModel<StorageBackendChoice>('selectedType', {required: true})
const s3 = defineModel<S3Form>('s3', {required: true})
const smb = defineModel<SmbForm>('smb', {required: true})
const sftp = defineModel<SftpForm>('sftp', {required: true})
const localRoot = defineModel<string>('localRoot')

const props = withDefaults(defineProps<{
    i18nPrefix: string
    probing: boolean
    saving: boolean
    types?: StorageBackendChoice[]
    probeOutcome?: ProbeResult | null
}>(), {
    types: () => ['LOCAL', 'S3', 'SMB', 'SFTP'],
    probeOutcome: null,
})

const emit = defineEmits<{
    'probe-config': []
    apply: []
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

        <StorageProbeOutcome :outcome="props.probeOutcome"/>

        <ButtonRow>
            <SecondaryButton :disabled="props.probing || selectedType === 'LOCAL' || selectedType === 'CLUSTER'"
                             @click="emit('probe-config')">
                {{
                    props.probing
                        ? t(`${props.i18nPrefix}.actions.probing`)
                        : t(`${props.i18nPrefix}.actions.probeConfig`)
                }}
            </SecondaryButton>
            <PrimaryButton :disabled="props.saving" data-testid="storage-backend-apply" @click="emit('apply')">
                {{
                    props.saving
                        ? t(`${props.i18nPrefix}.actions.applying`)
                        : t(`${props.i18nPrefix}.actions.apply`)
                }}
            </PrimaryButton>
        </ButtonRow>
    </NeutralContainer>
</template>
