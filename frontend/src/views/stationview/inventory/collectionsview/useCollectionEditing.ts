/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {inventoryCollections} from '@/api'
import type {InventoryCollection, ResolvedCollection, ResolvedCollectionLine} from '@/api/inventoryCollections'
import {moveWithin} from '@/util/reorder'

/**
 * The state behind the collections screen: which one is open, what it finds over the chosen window,
 * and every edit that writes back.
 *
 * <p>It lives beside the screen rather than in the view because the view would otherwise be a
 * hundred lines of refs before the first piece of markup. Every action reloads what it changed, so
 * the numbers beside a line are never a stale answer to an older question.
 */
export function useCollectionEditing() {
    const {t} = useI18n()

    const collections = ref<InventoryCollection[]>([])
    const selectedId = ref<number | null>(null)
    const resolved = ref<ResolvedCollection | null>(null)
    const error = ref('')
    const saving = ref(false)

    const dateFrom = ref('')
    const dateTo = ref('')

    const showNameModal = ref(false)
    const creating = ref(false)
    const draftName = ref('')
    const draftNote = ref('')

    const showLineModal = ref(false)
    const lineKind = ref<'item' | 'inventory'>('item')
    const lineItemId = ref('')
    const lineInventoryId = ref('')
    const lineQuantity = ref(1)

    const showDeleteModal = ref(false)

    async function reload() {
        collections.value = await inventoryCollections.list()
        if (selectedId.value !== null && !collections.value.some(entry => entry.id === selectedId.value)) {
            selectedId.value = null
        }
        if (selectedId.value === null) selectedId.value = collections.value[0]?.id ?? null
        await resolve()
    }

    async function resolve() {
        if (selectedId.value === null) {
            resolved.value = null
            return
        }
        resolved.value = await inventoryCollections.get(selectedId.value, {from: dateFrom.value, to: dateTo.value})
    }

    async function guard(action: () => Promise<void>) {
        error.value = ''
        saving.value = true
        try {
            await action()
        } catch {
            error.value = t('common.error')
        } finally {
            saving.value = false
        }
    }

    function select(id: number) {
        selectedId.value = id
        void guard(resolve)
    }

    function openCreate() {
        creating.value = true
        draftName.value = ''
        draftNote.value = ''
        showNameModal.value = true
    }

    function openRename() {
        creating.value = false
        draftName.value = resolved.value?.collection.name ?? ''
        draftNote.value = resolved.value?.collection.note ?? ''
        showNameModal.value = true
    }

    async function saveName() {
        await guard(async () => {
            if (creating.value) {
                const created = await inventoryCollections.create(draftName.value, draftNote.value)
                selectedId.value = created.id
            } else if (selectedId.value !== null) {
                await inventoryCollections.update(selectedId.value, draftName.value, draftNote.value)
            }
            showNameModal.value = false
            await reload()
        })
    }

    function askDelete() {
        showDeleteModal.value = true
    }

    async function confirmDelete() {
        await guard(async () => {
            if (selectedId.value === null) return
            await inventoryCollections.remove(selectedId.value)
            selectedId.value = null
            showDeleteModal.value = false
            await reload()
        })
    }

    function openAddLine() {
        lineKind.value = 'item'
        lineItemId.value = ''
        lineInventoryId.value = ''
        lineQuantity.value = 1
        showLineModal.value = true
    }

    async function addLine() {
        await guard(async () => {
            if (selectedId.value === null) return
            if (lineKind.value === 'item') {
                await inventoryCollections.addItemLine(selectedId.value, Number(lineItemId.value))
            } else {
                await inventoryCollections.addInventoryLine(
                    selectedId.value, Number(lineInventoryId.value), lineQuantity.value)
            }
            showLineModal.value = false
            await reload()
        })
    }

    async function changeQuantity(line: ResolvedCollectionLine, quantity: number) {
        if (quantity < 1 || selectedId.value === null) return
        await guard(async () => {
            await inventoryCollections.updateLineQuantity(selectedId.value as number, line.lineId, quantity)
            await resolve()
        })
    }

    async function removeLine(line: ResolvedCollectionLine) {
        await guard(async () => {
            if (selectedId.value === null) return
            await inventoryCollections.removeLine(selectedId.value, line.lineId)
            await reload()
        })
    }

    async function reorder(fromIndex: number, toIndex: number) {
        if (!resolved.value || selectedId.value === null) return
        const ordered = moveWithin(resolved.value.lines, fromIndex, toIndex).map(line => line.lineId)
        await guard(async () => {
            await inventoryCollections.reorderLines(selectedId.value as number, ordered)
            await resolve()
        })
    }

    watch([dateFrom, dateTo], () => void guard(resolve))

    return {
        collections,
        selectedId,
        resolved,
        error,
        saving,
        dateFrom,
        dateTo,
        showNameModal,
        creating,
        draftName,
        draftNote,
        showLineModal,
        lineKind,
        lineItemId,
        lineInventoryId,
        lineQuantity,
        showDeleteModal,
        reload,
        select,
        openCreate,
        openRename,
        saveName,
        askDelete,
        confirmDelete,
        openAddLine,
        addLine,
        changeQuantity,
        removeLine,
        reorder,
    }
}
