/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Board} from '@/api/generated/schema'

type DemoBoard = Pick<Board, 'id' | 'shortKey' | 'name' | 'description' | 'ticketCounter'>

export const demoBoards: DemoBoard[] = [
    {id: 1, shortKey: 'PLAN', name: 'Dienstplanung', description: 'Übungen, Dienste und Termine für das ganze Jahr', ticketCounter: 12},
    {id: 2, shortKey: 'AUS', name: 'Ausrüstung', description: 'Prüfungen und Reparaturen an Geräten und Fahrzeugen', ticketCounter: 5},
    {id: 3, shortKey: 'EVT', name: 'Sommerfest', description: 'Alles, was für das Sommerfest noch zu tun ist', ticketCounter: 1},
]
