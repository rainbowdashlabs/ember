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
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {SmbRequest} from '@/api/storageBackend'

const model = defineModel<SmbRequest>({required: true})
const {t} = useI18n()

const hostInvalid = computed(() => /:\/\//.test(model.value.host ?? ''))
</script>

<template>
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.smb.host') }}</FieldLabel>
            <TextInput v-model="model.host" placeholder="files.example.org" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.smb.hostHint') }}</MutedText>
            <p v-if="hostInvalid" class="text-xs text-(--error)">
                {{ t('stationStorageBackend.form.smb.hostInvalid') }}
            </p>
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.smb.port') }}</FieldLabel>
            <NumberInput v-model="model.port" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.smb.portHint') }}</MutedText>
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.smb.share') }}</FieldLabel>
            <TextInput v-model="model.share" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.smb.shareHint') }}</MutedText>
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.smb.domain') }}</FieldLabel>
            <TextInput v-model="model.domain" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.smb.domainHint') }}</MutedText>
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.smb.basePath') }}</FieldLabel>
            <TextInput v-model="model.basePath" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.smb.basePathHint') }}</MutedText>
        </div>
        <div class="space-y-1">
            <FieldLabel inline>
                <ToggleInput v-model="model.seal" />
                <span>{{ t('stationStorageBackend.form.smb.seal') }}</span>
            </FieldLabel>
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.smb.sealHint') }}</MutedText>
        </div>
        <div class="space-y-1">
            <FieldLabel inline>
                <ToggleInput v-model="model.dfs" />
                <span>{{ t('stationStorageBackend.form.smb.dfs') }}</span>
            </FieldLabel>
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.smb.dfsHint') }}</MutedText>
        </div>
        <div class="space-y-1">
            <FieldLabel>{{ t('stationStorageBackend.form.smb.username') }}</FieldLabel>
            <TextInput v-model="model.username" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.smb.usernameHint') }}</MutedText>
        </div>
        <div class="space-y-1 sm:col-span-2">
            <FieldLabel>{{ t('stationStorageBackend.form.smb.password') }}</FieldLabel>
            <PasswordInput v-model="model.password" />
            <MutedText tag="p" size="xs">{{ t('stationStorageBackend.form.smb.passwordHint') }}</MutedText>
        </div>
    </div>
</template>
