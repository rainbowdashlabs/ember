/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PasswordInput from '@/components/input/text/PasswordInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleSwitch from '@/components/input/toggle/ToggleSwitch.vue'
import type {SftpRequest} from '@/api/storageBackend'

const model = defineModel<SftpRequest>({required: true})
const {t} = useI18n()

const hostInvalid = computed(() => /:\/\//.test(model.value.host ?? ''))

const authMode = ref<'PASSWORD' | 'KEY'>(model.value.privateKey ? 'KEY' : 'PASSWORD')

watch(authMode, (next) => {
    if (next === 'KEY') {
        model.value.password = ''
    } else {
        model.value.privateKey = ''
    }
})
</script>

<template>
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.sftp.host') }}</FieldLabel>
            <TextInput v-model="model.host" placeholder="files.example.org" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.sftp.hostHint') }}</MutedText>
            <p v-if="hostInvalid" class="text-xs text-(--error)">
                {{ t('stationStorageBackend.form.sftp.hostInvalid') }}
            </p>
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.sftp.port') }}</FieldLabel>
            <NumberInput v-model="model.port" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.sftp.portHint') }}</MutedText>
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.sftp.username') }}</FieldLabel>
            <TextInput v-model="model.username" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.sftp.usernameHint') }}</MutedText>
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.sftp.knownHostsFingerprint') }}</FieldLabel>
            <TextInput v-model="model.knownHostsFingerprint" placeholder="SHA256:…" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.sftp.knownHostsFingerprintHint') }}</MutedText>
        </div>
        <div class="space-y-1 sm:col-span-2">
            <FieldLabel>{{ t('stationStorageBackend.form.sftp.basePath') }}</FieldLabel>
            <TextInput v-model="model.basePath" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.sftp.basePathHint') }}</MutedText>
        </div>
        <div class="space-y-1 sm:col-span-2">
            <ToggleSwitch
                v-model="authMode"
                option-a="PASSWORD"
                option-b="KEY"
                :label-a="t('stationStorageBackend.form.sftp.authMode.password')"
                :label-b="t('stationStorageBackend.form.sftp.authMode.privateKey')"
            />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.sftp.authMode.hint') }}</MutedText>
        </div>
        <div v-if="authMode === 'PASSWORD'" class="space-y-1 sm:col-span-2">
            <FieldLabel>{{ t('stationStorageBackend.form.sftp.password') }}</FieldLabel>
            <PasswordInput v-model="model.password" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.sftp.passwordHint') }}</MutedText>
        </div>
        <div v-else class="space-y-1 sm:col-span-2">
            <FieldLabel>{{ t('stationStorageBackend.form.sftp.privateKey') }}</FieldLabel>
            <TextAreaInput v-model="model.privateKey" rows="6" placeholder="-----BEGIN OPENSSH PRIVATE KEY-----" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.sftp.privateKeyHint') }}</MutedText>
        </div>
    </div>
</template>
