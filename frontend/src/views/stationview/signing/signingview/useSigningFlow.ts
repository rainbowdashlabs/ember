/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {FillInResponse, OpenSignatureResponse} from '@/api/generated/schema'
import {getOpenSignatures, getSigningFillIns} from '@/api/signing'
import type {SignatureDraft} from '@/util/signatureDraft'
import {
    batchChoices,
    chosenGroups,
    flowSteps,
    groupByDocument,
    preselected,
    throughAccountSigners,
    type FlowStep,
} from './batchFlow'
import type {FillInValues} from './fillIns'
import {useBatchSigning, type SigningMarkChoice, type SigningPictures} from './useBatchSigning'

/**
 * Everything the signing flow keeps while the reader walks through it: the fields waiting for them, which
 * are ticked, what each statement box and fill-in holds, the pictures, the step they are on, and the act.
 *
 * <p>It all lives here rather than in the screens, so going back and forth keeps every tick, box, typed
 * value and drawn picture; a screen only shows and changes what it is handed.
 *
 * @param firstFieldId the field the reader came for, whose document comes first, or null
 * @param named        the fields the address names to tick, empty to tick every field
 */
export function useSigningFlow(firstFieldId: () => number | null, named: () => number[]) {
    const {t} = useI18n()
    const fields = ref<OpenSignatureResponse[]>([])
    const fillIns = ref<Record<number, FillInResponse[]>>({})
    const chosen = ref<number[]>([])
    const agreed = ref<Record<number, boolean>>({})
    const values = ref<Record<number, FillInValues>>({})
    const holderMark = ref<SigningMarkChoice>({draft: null, useSaved: true, keep: false})
    const memberMarks = ref<Record<number, SignatureDraft | null>>({})
    const stepIndex = ref(0)

    const groups = computed(() => groupByDocument(fields.value, t('signing.flow.untitled'), firstFieldId()))
    const selected = computed(() => chosenGroups(groups.value, chosen.value))
    const steps = computed(() => flowSteps(selected.value))
    const step = computed<FlowStep>(() => steps.value[Math.min(stepIndex.value, steps.value.length - 1)] ?? {kind: 'overview'})
    const position = computed(() => Math.min(stepIndex.value, steps.value.length - 1) + 1)

    const act = useBatchSigning(() => batchChoices(selected.value, fillIns.value, values.value))

    /** Reads the fields waiting for the reader and what each asks to fill in, and ticks them. */
    async function load() {
        const open = await getOpenSignatures()
        const asked = await Promise.all(open.map(field => getSigningFillIns(field.fieldId)))
        fields.value = open
        fillIns.value = Object.fromEntries(open.map((field, index) => [field.fieldId, asked[index] ?? []]))
        chosen.value = preselected(open, named())
    }

    /** The pictures of the confirmation: the reader's, and those of the members who sign now in person. */
    function pictures(): SigningPictures {
        const signing = throughAccountSigners(selected.value.flatMap(group => group.fields))
        const members = Object.fromEntries(signing.map(signer => [signer.memberId, memberMarks.value[signer.memberId] ?? null]))
        return {holder: holderMark.value, members}
    }

    function setMemberMark(memberId: number, draft: SignatureDraft | null) {
        memberMarks.value = {...memberMarks.value, [memberId]: draft}
    }

    function next() {
        stepIndex.value = Math.min(stepIndex.value + 1, steps.value.length - 1)
    }

    function back() {
        stepIndex.value = Math.max(stepIndex.value - 1, 0)
    }

    return {
        fields,
        fillIns,
        chosen,
        agreed,
        values,
        holderMark,
        memberMarks,
        groups,
        selected,
        steps,
        step,
        position,
        act,
        load,
        pictures,
        setMemberMark,
        next,
        back,
    }
}
