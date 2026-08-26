/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {fieldTemplates, type FieldTemplate} from './fieldTemplates'
import {useFieldsCapabilities} from '@/composables/useFieldsConfig'

const emit = defineEmits<{
  (e: 'apply', tpl: FieldTemplate): void
}>()

const capabilities = useFieldsCapabilities()

/**
 * A template that names a type its owner may not use does not offer itself. An association may ask
 * for many things but not for a date of birth, because the station declares its own and the two would
 * collide, so the one template built on that type simply is not there.
 */
const available = computed(() => fieldTemplates.filter(
    tpl => tpl.fields.every(f => capabilities.types.includes(f.fieldType))))
</script>

<template>
  <div class="flex flex-wrap gap-2">
    <SecondaryButton
        v-for="tpl in available"
        :key="tpl.name"
        :icon="['fas', tpl.icon]"
        @click="emit('apply', tpl)"
    >
      {{ tpl.name }}
    </SecondaryButton>
  </div>
</template>
