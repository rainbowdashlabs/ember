/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {S3Request} from '@/api/storageBackend'

const model = defineModel<S3Request>({required: true})
const {t} = useI18n()
</script>

<template>
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.s3.endpoint') }}</FieldLabel>
            <TextInput v-model="model.endpoint" placeholder="https://s3.amazonaws.com" />
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.s3.region') }}</FieldLabel>
            <TextInput v-model="model.region" placeholder="eu-central-1" />
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.s3.bucket') }}</FieldLabel>
            <TextInput v-model="model.bucket" />
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.s3.basePath') }}</FieldLabel>
            <TextInput v-model="model.basePath" />
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.s3.sseAlgorithm') }}</FieldLabel>
            <TextInput v-model="model.sseAlgorithm" placeholder="AES256" />
        </div>
        <FieldLabel inline>
            <ToggleInput v-model="model.pathStyle" />
            <span>{{ t('stationStorageBackend.form.s3.pathStyle') }}</span>
        </FieldLabel>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.s3.accessKey') }}</FieldLabel>
            <TextInput v-model="model.accessKey" />
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.s3.secretKey') }}</FieldLabel>
            <TextInput v-model="model.secretKey" />
        </div>
    </div>
</template>
