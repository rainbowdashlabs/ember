/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldHint from '@/components/typography/FieldHint.vue'

const {t} = useI18n()

/**
 * Whether this entry stands on the public wiki.
 *
 * <p>Shown even where the wiki is not public at all, saying so, rather than hidden. Hiding it is how the
 * setting became impossible to find: somebody looking for it saw nothing and no reason for nothing.
 */
defineProps<{
    disabled?: boolean
}>()

const modelValue = defineModel<string>({required: true})
</script>

<template>
    <div class="space-y-2 border-t border-bg-light-accent dark:border-bg-dark-accent pt-3">
        <SubHeader class="text-sm">{{ t('kb.publicVisibility') }}</SubHeader>
        <SelectInput v-model="modelValue" :disabled="disabled">
            <option value="default">{{ t('kb.publicVisibilityDefault') }}</option>
            <option value="public">{{ t('kb.publicVisibilityPublic') }}</option>
            <option value="hidden">{{ t('kb.publicVisibilityHidden') }}</option>
        </SelectInput>
        <FieldHint v-if="disabled">{{ t('kb.publicVisibilityOff') }}</FieldHint>
    </div>
</template>
