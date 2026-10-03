/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import {emptyRestriction} from '@/components/input/restriction'
import type {RestrictionSelection} from '@/api/types'
import type {BlockRestrictionChoices} from '@/composables/useBlockEditorOptions'

/**
 * Who one block is shown to, chosen with the shared audience editor. A block nobody is chosen for is
 * shown to everybody; a restricted one is left out wherever it does not match.
 */
const open = defineModel<boolean>({required: true})
const restriction = defineModel<RestrictionSelection | null | undefined>('restriction', {required: true})

defineProps<{
    choices: BlockRestrictionChoices
}>()

const {t} = useI18n()

const selection = computed<RestrictionSelection>({
    get: () => restriction.value ?? emptyRestriction(),
    set: (value) => {
        restriction.value = value
    },
})
</script>

<template>
    <Modal v-model="open">
        <div class="space-y-4" data-testid="cell-visibility">
            <SectionHeader>{{ t('stationPages.editor.visibility') }}</SectionHeader>
            <MutedText size="sm" tag="p">{{ t('stationPages.editor.visibilityHint') }}</MutedText>
            <RestrictionsField v-model="selection" :groups="choices.groups" :tags="choices.tags"/>
        </div>
    </Modal>
</template>
