/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The station statistics payload, as the summary cards and the charts both read it.
 */
export interface StatsData {
  memberCount: number
  groupCounts: Record<string, number>
  attendanceByMonth: Array<{ month: string; sessions: number; present: number; absent: number; declined: number }>
  inventoryStatus: Array<{ name: string; total: number; assigned: number; lost: number }>
  eventRegistrations: Array<{ name: string; accepted: number; pending: number; declined: number }>
  roleCounts: Record<string, number>
}
