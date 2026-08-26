/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EventFieldValueInput from './EventFieldValueInput.vue'
import type {EventRegistrationField, RegistrationFieldValue} from '@/api/events'

/** One person an answer can be given for. */
export interface AnswerablePerson {
    memberId: number
    name: string
}

/** One person's answer, ready to be sent. */
export interface PersonAnswer {
    memberId: number
    fields: RegistrationFieldValue[]
}

/**
 * Answering for a household in one go.
 *
 * <p>A guardian with two children and a membership of their own used to walk the same screen three
 * times. Here they tick who the answer is for and give it once.
 *
 * <p>Where the event asks questions, each person gets a tile of their own, because the answers are theirs
 * rather than the household's: one child's shirt size is not the other's. Declining asks nothing, so the
 * tiles are absent then and the dialog is a list of names.
 */
const props = defineProps<{
    people: AnswerablePerson[]
    /** The questions the event asks. Empty when it asks none, and ignored when declining. */
    fields: EventRegistrationField[]
    /** Whether this dialog is saying yes. Saying no needs no answers. */
    attending: boolean
    busy?: boolean
    error?: string
}>()

const show = defineModel<boolean>({required: true})

const emit = defineEmits<{
    confirm: [answers: PersonAnswer[]]
}>()

const {t} = useI18n()

const chosen = ref<number[]>([])
const answers = ref<Record<number, Record<number, string>>>({})

const asksQuestions = computed(() => props.attending && props.fields.length > 0)

const missing = computed(() => {
    if (!asksQuestions.value) return []
    return chosen.value.filter(memberId => props.fields.some(
        field => field.config?.required && !(answers.value[memberId]?.[field.id] ?? '').trim()))
})

/** Everyone is ticked when the dialog opens: answering for the whole household is the common case. */
watch(show, (visible) => {
    if (!visible) return
    chosen.value = props.people.map(person => person.memberId)
    answers.value = Object.fromEntries(props.people.map(person => [person.memberId, {}]))
})

function toggle(memberId: number) {
    chosen.value = chosen.value.includes(memberId)
        ? chosen.value.filter(id => id !== memberId)
        : [...chosen.value, memberId]
}

function nameOf(memberId: number): string {
    return props.people.find(person => person.memberId === memberId)?.name ?? String(memberId)
}

function answerFor(memberId: number, fieldId: number): string {
    return answers.value[memberId]?.[fieldId] ?? ''
}

function setAnswer(memberId: number, fieldId: number, value: string) {
    answers.value = {...answers.value, [memberId]: {...answers.value[memberId], [fieldId]: value}}
}

function confirm() {
    if (chosen.value.length === 0 || missing.value.length > 0) return
    emit('confirm', chosen.value.map(memberId => ({
        memberId,
        fields: props.fields.map(field => ({fieldId: field.id, value: answerFor(memberId, field.id)})),
    })))
}
</script>

<template>
    <Modal v-model="show" size="lg">
        <SubHeader class="mb-1">
            {{ attending ? t('events.answerFor') : t('events.declineFor') }}
        </SubHeader>
        <p class="mb-3 text-xs text-(--text-muted)">{{ t('events.answerForHint') }}</p>

        <Alert v-if="error" variant="error" class="mb-3">{{ error }}</Alert>

        <div class="space-y-1 mb-4">
            <label
                v-for="person in people"
                :key="person.memberId"
                class="flex items-center gap-2 text-sm"
            >
                <input
                    type="checkbox"
                    :checked="chosen.includes(person.memberId)"
                    :data-testid="`answer-for-${person.memberId}`"
                    @change="toggle(person.memberId)"
                />
                {{ person.name }}
            </label>
        </div>

        <div v-if="asksQuestions" class="space-y-3 mb-4">
            <NeutralContainer v-for="memberId in chosen" :key="memberId" class="space-y-2">
                <FieldLabel>{{ nameOf(memberId) }}</FieldLabel>
                <div v-for="field in fields" :key="field.id" class="space-y-1">
                    <FieldLabel>{{ field.name }}{{ field.config?.required ? ' *' : '' }}</FieldLabel>
                    <EventFieldValueInput
                        :field-type="field.fieldType"
                        :config="{...field.config}"
                        :model-value="answerFor(memberId, field.id)"
                        @update:model-value="value => setAnswer(memberId, field.id, value)"
                    />
                </div>
            </NeutralContainer>
        </div>

        <div class="flex justify-end gap-2">
            <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton
                :disabled="busy || chosen.length === 0 || missing.length > 0"
                data-testid="answer-confirm"
                @click="confirm"
            >
                {{ t('common.save') }}
            </PrimaryButton>
        </div>
    </Modal>
</template>
