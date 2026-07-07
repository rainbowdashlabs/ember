/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'

const props = defineProps<{
  provider: string
  secretPlaceholder?: string
}>()

const user = defineModel<string>('user', {required: true})
const secret = defineModel<string>('secret', {required: true})

const {t} = useI18n()

const hasUser = computed(() => props.provider !== 'TWILIO')

const messageKey = (name: string) => `mailProviders.${props.provider}.${name}`
</script>

<template>
  <div class="space-y-3">
    <p class="text-xs text-(--text-muted)">{{ t(messageKey('intro')) }}</p>
    <div class="grid gap-4 sm:grid-cols-2">
      <div v-if="hasUser" class="space-y-1">
        <FieldLabel>{{ t(messageKey('userLabel')) }}</FieldLabel>
        <TextInput v-model="user"/>
        <p class="text-xs text-(--text-muted)">{{ t(messageKey('userHint')) }}</p>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t(messageKey('secretLabel')) }}</FieldLabel>
        <TextInput v-model="secret" type="password" :placeholder="secretPlaceholder"/>
        <p class="text-xs text-(--text-muted)">{{ t(messageKey('secretHint')) }}</p>
      </div>
    </div>
  </div>
</template>
