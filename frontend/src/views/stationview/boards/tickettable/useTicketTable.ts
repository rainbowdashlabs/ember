/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type MaybeRefOrGetter, toValue} from 'vue'
import {useI18n} from 'vue-i18n'
import type {BoardLabel, BoardTicket} from '@/api/boards'
import type {MemberCompletion} from '@/api/stationMembers'
import {ColumnTypes, type TableColumn} from '@/components/table/tableColumn'
import {useDataTable} from '@/composables/useDataTable'
import {priorityOptions} from '@/util/ticketPriority'

export interface TicketTableOptions {
  /** Names the table where its column choices are remembered. */
  id: string
  shortKey: MaybeRefOrGetter<string>
  tickets: MaybeRefOrGetter<readonly BoardTicket[]>
  /** The station's members, which is where an assignee's name is read from. */
  members: MaybeRefOrGetter<readonly MemberCompletion[]>
  /** The labels a ticket wears. Without it the table has no label column. */
  labelsOf?: (ticket: BoardTicket) => BoardLabel[]
}

/**
 * The tickets of one board as a table: their key, title, priority, assignee and due date, and the
 * labels where a screen shows them. Unsorted, the tickets keep the order they stand in on the board.
 */
export function useTicketTable(options: TicketTableOptions) {
  const {t} = useI18n()

  const priorities = computed(() => priorityOptions(t))

  const memberNames = computed(() => new Map(toValue(options.members).map(member => [member.memberUid, member.name])))

  function assigneeName(ticket: BoardTicket): string {
    const uid = ticket.assignee?.memberUid
    if (!uid) return ''
    return memberNames.value.get(uid) ?? ''
  }

  function labelColumns(): TableColumn<BoardTicket>[] {
    const labelsOf = options.labelsOf
    if (!labelsOf) return []
    return [{key: 'labels', label: t('boards.labels'), type: ColumnTypes.TEXT, value: ticket => labelsOf(ticket).map(label => label.name)}]
  }

  const columns = computed<TableColumn<BoardTicket>[]>(() => [
    {
      key: 'key', label: t('boards.ticketKey'), type: ColumnTypes.TEXT,
      value: ticket => `${toValue(options.shortKey)}-${ticket.ticketNumber}`, sortValue: ticket => ticket.ticketNumber,
    },
    {key: 'title', label: t('boards.ticketTitle'), type: ColumnTypes.TEXT, value: ticket => ticket.title, pinned: true},
    ...labelColumns(),
    {key: 'priority', label: t('boards.priority'), type: ColumnTypes.ENUM, value: ticket => ticket.priority, options: priorities.value},
    {key: 'assignee', label: t('boards.assignee'), type: ColumnTypes.TEXT, value: assigneeName},
    {key: 'dueDate', label: t('boards.dueDate'), type: ColumnTypes.DATE, value: ticket => ticket.dueDate},
  ])

  return useDataTable<BoardTicket>({
    id: options.id,
    rows: options.tickets,
    columns,
    rowKey: ticket => ticket.id,
    fallbackSort: (a, b) => a.position - b.position,
  })
}

export type TicketTableApi = ReturnType<typeof useTicketTable>
