/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import rule from './confirm-through-composable.mjs'
import {vueRuleTester} from './rule-tester'

/**
 * A confirmation has to be opened through the composables that let shift skip it, unless it asks every
 * time on purpose and marks it so.
 *
 * @vitest-environment node
 */
const sfc = (script: string, template: string) =>
    `<script setup lang="ts">\n${script}\n</script>\n<template>${template}</template>`
const confirm = (model: string, extra = '') =>
    `<ConfirmDeleteModal ${model} ${extra} message="Weg?" @confirm="go"/>`

vueRuleTester().run('confirm-through-composable', rule, {
    valid: [
        {code: sfc('const {show: asking, confirm: go} = useConfirmDelete({onDelete})', confirm('v-model="asking"'))},
        {code: sfc('const removal = useConfirmDelete({onDelete})', confirm('v-model="removal.show.value"'))},
        {code: sfc('const removal = useConfirmAction({onConfirm})\nconst {show} = removal', confirm('v-model="show"'))},
        {code: sfc('const invite = useListInvites(id)', confirm('v-model="invite.confirmingRemove.value"'))},
        {code: sfc('const {bulkDeleteOpen} = useMediaBulkActions(deps)', confirm('v-model="bulkDeleteOpen"'))},
        {code: sfc('const open = defineModel<boolean>()', confirm(':model-value="open"'))},
        {
            code: sfc('const props = defineProps<{actions: ReturnType<typeof useQueueActions>}>()\n'
                + 'const open = computed(() => props.actions.question.value)', confirm('v-model="open"')),
        },
        {code: sfc('const asking = ref(false)', confirm('v-model="asking"', 'data-confirm-always'))},
        {
            code: sfc('const removal = useConfirmDelete({onDelete})',
                '<ConfirmDeleteModal v-if="removal.show.value" :model-value="true" message="x"/>'),
        },
        {code: sfc('', '<Modal :model-value="true"><DeleteButton @click="ask"/></Modal>')},
        {code: sfc('const shown = ref(false)', '<ConfirmedRegistrationsTable v-model="shown"/>')},
        {code: sfc('const editing = ref(false)', '<Modal v-model="editing"><PrimaryButton>Speichern</PrimaryButton></Modal>')},
        {code: sfc('const pending = ref(false)', '<TransitionConfirmModal v-model:message="text" :pending="pending"/>')},
        {code: sfc('const {show: asking} = useConfirmAction({onConfirm})', '<LeaveConfirmModal v-model="asking"/>')},
    ],
    invalid: [
        {code: sfc('const asking = ref(false)', confirm('v-model="asking"')), errors: [{messageId: 'byHand'}]},
        {code: sfc('const pending = ref(null)', confirm(':model-value="pending !== null"')), errors: [{messageId: 'byHand'}]},
        {code: sfc('const removal = useConfirmDelete({onDelete})', confirm('v-model="removal.target.value"')), errors: [{messageId: 'byHand'}]},
        {
            code: sfc('const asking = ref(false)', '<Modal v-model="asking"><DeleteButton @click="go"/></Modal>'),
            errors: [{messageId: 'byHand'}],
        },
        {
            code: sfc('const asking = ref(false)', '<Modal v-model="asking"><div><ErrorButton @click="go">Weg</ErrorButton></div></Modal>'),
            errors: [{messageId: 'byHand'}],
        },
        {
            code: sfc('const asking = ref(false)', '<PasskeyRemoveConfirmDialog v-model="asking"/>'),
            errors: [{messageId: 'openedByFlag', data: {tag: 'PasskeyRemoveConfirmDialog'}}],
        },
        {
            code: sfc('const asking = ref(false)', '<BulkDeleteModals v-model="asking"/>'),
            errors: [{messageId: 'openedByFlag', data: {tag: 'BulkDeleteModals'}}],
        },
        {
            code: sfc('const {isOpen} = useModalTarget()', '<PasskeyRemoveModal v-model="isOpen"/>'),
            errors: [{messageId: 'openedByFlag', data: {tag: 'PasskeyRemoveModal'}}],
        },
        {
            code: sfc('const target = ref(null)\nconst open = computed(() => target.value !== null)',
                '<StorageApplyConfirmModal v-model="open"/>'),
            errors: [{messageId: 'openedByFlag', data: {tag: 'StorageApplyConfirmModal'}}],
        },
        {
            code: sfc('const target = ref(null)', '<LeaveConfirmModal :model-value="target !== null"/>'),
            errors: [{messageId: 'openedByFlag', data: {tag: 'LeaveConfirmModal'}}],
        },
        {
            code: sfc('const asking = ref(false)', '<ConfirmDeleteModal v-if="asking" :model-value="true" message="x"/>'),
            errors: [{messageId: 'byHand'}],
        },
        {
            code: sfc('const asking = ref(false)', '<Modal v-if="asking" :model-value="true"><ErrorButton>Weg</ErrorButton></Modal>'),
            errors: [{messageId: 'byHand'}],
        },
    ],
})
