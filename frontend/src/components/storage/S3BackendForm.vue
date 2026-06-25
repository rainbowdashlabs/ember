/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PasswordInput from '@/components/input/text/PasswordInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {S3Request} from '@/api/storageBackend'

const model = defineModel<S3Request>({required: true})
const {t} = useI18n()

const endpointInvalid = computed(() => {
    const v = model.value.endpoint?.trim() ?? ''
    if (v.length === 0) return false
    return !/^https?:\/\//i.test(v)
})
</script>

<template>
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.s3.endpoint') }}</FieldLabel>
            <TextInput v-model="model.endpoint" placeholder="https://s3.amazonaws.com" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.s3.endpointHint') }}</MutedText>
            <p v-if="endpointInvalid" class="text-xs text-(--error)">
                {{ t('stationStorageBackend.form.s3.endpointInvalid') }}
            </p>
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.s3.region') }}</FieldLabel>
            <TextInput v-model="model.region" placeholder="eu-central-1" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.s3.regionHint') }}</MutedText>
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.s3.bucket') }}</FieldLabel>
            <TextInput v-model="model.bucket" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.s3.bucketHint') }}</MutedText>
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.s3.basePath') }}</FieldLabel>
            <TextInput v-model="model.basePath" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.s3.basePathHint') }}</MutedText>
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.s3.sseAlgorithm') }}</FieldLabel>
            <TextInput v-model="model.sseAlgorithm" placeholder="AES256" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.s3.sseAlgorithmHint') }}</MutedText>
        </div>
        <div class="space-y-1">
            <FieldLabel inline>
                <ToggleInput v-model="model.pathStyle" />
                <span>{{ t('stationStorageBackend.form.s3.pathStyle') }}</span>
            </FieldLabel>
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.s3.pathStyleHint') }}</MutedText>
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.s3.accessKey') }}</FieldLabel>
            <TextInput v-model="model.accessKey" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.s3.accessKeyHint') }}</MutedText>
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.s3.secretKey') }}</FieldLabel>
            <PasswordInput v-model="model.secretKey" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.s3.secretKeyHint') }}</MutedText>
        </div>
    </div>
</template>
