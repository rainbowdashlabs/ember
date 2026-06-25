/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'

defineProps<{
    modelValue: string
}>()

const {t} = useI18n()
</script>

<template>
    <SelectInput :model-value="modelValue">
        <option value="STRING">{{ t('boards.fieldTypeString') }}</option>
        <option value="NUMBER">{{ t('boards.fieldTypeNumber') }}</option>
        <option value="BOOLEAN">{{ t('boards.fieldTypeBoolean') }}</option>
        <option value="ENUM">{{ t('boards.fieldTypeEnum') }}</option>
        <option value="DATE">{{ t('boards.fieldTypeDate') }}</option>
        <option value="LANE_ASSIGNEE">{{ t('boards.fieldTypeLaneAssignee') }}</option>
    </SelectInput>
</template>
