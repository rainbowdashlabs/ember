/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'

/**
 * The one confirmation before a storage change that moves or drops files, on every storage screen.
 *
 * The body says what is about to happen to whose files; the screen writes it, because only the screen knows.
 * Bind `keepSource` where the old files may stay behind as well, and the choice is offered here.
 */
const props = defineProps<{
    title: string
    body: string | null
    saving: boolean
}>()

const emit = defineEmits<{
    confirm: []
    cancel: []
}>()

const keepSource = defineModel<boolean>('keepSource')

const open = computed({
    get: () => props.body !== null,
    set: (value: boolean) => {
        if (!value) emit('cancel')
    },
})

const {t} = useI18n()
</script>

<template>
    <Modal v-model="open" size="md">
        <div class="space-y-4" data-testid="storage-confirm">
            <SubHeader>{{ title }}</SubHeader>
            <MutedText tag="p" size="sm">{{ body }}</MutedText>
            <FieldLabel v-if="keepSource !== undefined" inline>
                <ToggleInput v-model="keepSource"/>
                <span>{{ t('storageBackend.confirm.keepSource') }}</span>
            </FieldLabel>
            <ButtonRow pair align="end">
                <SecondaryButton @click="emit('cancel')">{{ t('storageBackend.confirm.cancel') }}</SecondaryButton>
                <PrimaryButton :disabled="saving" data-testid="storage-confirm-go" @click="emit('confirm')">
                    {{ t('storageBackend.confirm.confirm') }}
                </PrimaryButton>
            </ButtonRow>
        </div>
    </Modal>
</template>
