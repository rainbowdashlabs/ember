/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {ProfileField} from '@/api/profileFields'

/**
 * Group fields that name no group. Such a field is only ever shown at its group, so one without
 * belongs nowhere and would stay out of reach. Opening it and saving it puts it in the chosen group.
 */
defineProps<{ fields: ProfileField[] }>()

const emit = defineEmits<{
  edit: [field: ProfileField]
}>()

const {t} = useI18n()
</script>

<template>
  <Alert variant="info">
    <p>{{ t('membersConfig.unassignedGroupFields') }}</p>
    <div class="flex flex-wrap gap-2 mt-2">
      <SecondaryButton v-for="field in fields" :key="field.id" @click="emit('edit', field)">
        {{ field.name }}
      </SecondaryButton>
    </div>
  </Alert>
</template>
