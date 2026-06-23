/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import type {SftpRequest} from '@/api/storageBackend'

const model = defineModel<SftpRequest>({required: true})
const {t} = useI18n()
</script>

<template>
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.sftp.host') }}</FieldLabel>
            <TextInput v-model="model.host" />
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.sftp.port') }}</FieldLabel>
            <NumberInput v-model="model.port" />
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.sftp.username') }}</FieldLabel>
            <TextInput v-model="model.username" />
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.sftp.knownHostsFingerprint') }}</FieldLabel>
            <TextInput v-model="model.knownHostsFingerprint" />
        </div>
        <div class="space-y-1 sm:col-span-2">
            <FieldLabel>{{ t('stationStorageBackend.form.sftp.basePath') }}</FieldLabel>
            <TextInput v-model="model.basePath" />
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.sftp.password') }}</FieldLabel>
            <TextInput v-model="model.password" />
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.sftp.privateKey') }}</FieldLabel>
            <TextInput v-model="model.privateKey" />
        </div>
    </div>
</template>
