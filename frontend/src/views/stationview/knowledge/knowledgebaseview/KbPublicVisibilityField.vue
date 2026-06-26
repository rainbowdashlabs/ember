/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const {t} = useI18n()

const modelValue = defineModel<string>({required: true})
</script>

<template>
    <div class="space-y-2 border-t border-bg-light-accent dark:border-bg-dark-accent pt-3">
        <SubHeader class="text-sm">{{ t('kb.publicVisibility') }}</SubHeader>
        <SelectInput v-model="modelValue">
            <option value="default">{{ t('kb.publicVisibilityDefault') }}</option>
            <option value="public">{{ t('kb.publicVisibilityPublic') }}</option>
            <option value="hidden">{{ t('kb.publicVisibilityHidden') }}</option>
        </SelectInput>
    </div>
</template>
