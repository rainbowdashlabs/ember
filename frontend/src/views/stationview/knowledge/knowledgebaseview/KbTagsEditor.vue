/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'

const {t} = useI18n()

defineProps<{
    /** Names the list, for the places that hold more than one of these at once. */
    label?: string
}>()

const modelValue = defineModel<string[]>({required: true})

const newTag = ref('')

function addTag() {
    const trimmed = newTag.value.trim()
    if (!trimmed) return
    modelValue.value = [...modelValue.value, trimmed]
    newTag.value = ''
}

function removeTag(tag: string) {
    modelValue.value = modelValue.value.filter(t => t !== tag)
}
</script>

<template>
    <div class="space-y-2 border-t border-bg-light-accent dark:border-bg-dark-accent pt-3">
        <label class="text-sm font-semibold">{{ label ?? t('kb.tags') }}</label>
        <div class="flex flex-wrap gap-1.5">
            <span v-for="tag in modelValue" :key="tag"
                  class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs bg-[var(--bg-accent)] text-[var(--text)]">
                {{ tag }}
                <IconButton
                    :icon="['fas', 'xmark']"
                    :label="t('common.remove')"
                    class="!p-0 !text-[10px]"
                    @click="removeTag(tag)"
                />
            </span>
        </div>
        <form class="flex gap-2" @submit.prevent="addTag">
            <TextInput v-model="newTag" :placeholder="t('kb.tagPlaceholder')" class="flex-1"/>
            <SecondaryButton type="submit" :disabled="!newTag.trim()">
                <font-awesome-icon :icon="['fas', 'plus']"/>
            </SecondaryButton>
        </form>
    </div>
</template>
