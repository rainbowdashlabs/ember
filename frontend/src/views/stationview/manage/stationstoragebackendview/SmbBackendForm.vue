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
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {SmbRequest} from '@/api/storageBackend'

const model = defineModel<SmbRequest>({required: true})
const {t} = useI18n()
</script>

<template>
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.smb.host') }}</FieldLabel>
            <TextInput v-model="model.host" />
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.smb.port') }}</FieldLabel>
            <NumberInput v-model="model.port" />
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.smb.share') }}</FieldLabel>
            <TextInput v-model="model.share" />
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.smb.domain') }}</FieldLabel>
            <TextInput v-model="model.domain" />
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.smb.basePath') }}</FieldLabel>
            <TextInput v-model="model.basePath" />
        </div>
        <FieldLabel inline>
            <ToggleInput v-model="model.seal" />
            <span>{{ t('stationStorageBackend.form.smb.seal') }}</span>
        </FieldLabel>
        <FieldLabel inline>
            <ToggleInput v-model="model.dfs" />
            <span>{{ t('stationStorageBackend.form.smb.dfs') }}</span>
        </FieldLabel>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.smb.username') }}</FieldLabel>
            <TextInput v-model="model.username" />
        </div>
        <div class="space-y-1 sm:col-span-2">
            <FieldLabel>{{ t('stationStorageBackend.form.smb.password') }}</FieldLabel>
            <TextInput v-model="model.password" />
        </div>
    </div>
</template>
