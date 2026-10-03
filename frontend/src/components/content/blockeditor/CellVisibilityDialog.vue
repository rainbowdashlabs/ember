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
import LabelledField from '@/components/input/LabelledField.vue'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {emptyRestriction} from '@/components/input/restriction'
import {GuardianCondition} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'
import type {BlockRestrictionChoices} from '@/composables/useBlockEditorOptions'

const open = defineModel<boolean>({required: true})
const restriction = defineModel<RestrictionSelection | null | undefined>('restriction', {required: true})
/**
 * Which guardians the member must have for the block to be printed, null for no such condition, or
 * undefined where the editor offers no such condition.
 */
const guardianCondition = defineModel<GuardianCondition | null | undefined>('guardianCondition')

defineProps<{
    choices: BlockRestrictionChoices
}>()

const {t} = useI18n()

const CONDITIONS: readonly GuardianCondition[] = [GuardianCondition.SECOND_GUARDIAN, GuardianCondition.NO_SECOND_GUARDIAN]

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
            <LabelledField v-if="guardianCondition !== undefined" :label="t('stationPages.editor.guardianCondition')"
                           :help="t('stationPages.editor.guardianConditionHint')">
                <SelectInput :model-value="guardianCondition ?? ''" data-testid="cell-guardian-condition"
                             @update:model-value="value => guardianCondition = value ? value as GuardianCondition : null">
                    <option value="">{{ t('stationPages.editor.guardianConditionNone') }}</option>
                    <option v-for="condition in CONDITIONS" :key="condition" :value="condition">
                        {{ t(`stationPages.editor.guardianConditionOf.${condition}`) }}
                    </option>
                </SelectInput>
            </LabelledField>
        </div>
    </Modal>
</template>
