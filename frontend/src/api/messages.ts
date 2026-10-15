/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    ConversationMessagePage,
    ConversationMessageResponse,
    ConversationResponse,
    ConversationSummaryResponse,
    MessagePersonResponse,
    MessagesStatus,
    ReachablePersonResponse,
} from './generated/schema'

/** Where the stream of message signals is opened, which the browser reaches with its cookie. */
export const MESSAGE_EVENTS_URL = '/api/v1/messages/events'

/** Which way a page of a conversation is read: back from a message, or on from it. */
export interface MessagePageQuery {
    before?: number
    after?: number
}

function conversationPath(uid: string): string {
    return `/messages/conversations/${uid}`
}

function messagePath(uid: string, messageId: number): string {
    return `${conversationPath(uid)}/messages/${messageId}`
}

export async function getStatus(): Promise<MessagesStatus> {
    const res = await client.get<MessagesStatus>('/messages/status')
    return res.data
}

export async function setMail(mail: boolean): Promise<MessagesStatus> {
    const res = await client.put<MessagesStatus>('/messages/settings', {mail})
    return res.data
}

export async function listConversations(): Promise<ConversationSummaryResponse[]> {
    const res = await client.get<ConversationSummaryResponse[]>('/messages/conversations')
    return res.data
}

/** Opens the direct conversation with somebody, starting it where there is none yet. */
export async function openDirect(personUid: string): Promise<ConversationResponse> {
    const res = await client.post<ConversationResponse>('/messages/conversations', {kind: 'DIRECT', people: [personUid]})
    return res.data
}

export async function startGroup(people: string[], name: string | null): Promise<ConversationResponse> {
    const res = await client.post<ConversationResponse>('/messages/conversations', {kind: 'GROUP', people, name})
    return res.data
}

export async function getConversation(uid: string): Promise<ConversationResponse> {
    const res = await client.get<ConversationResponse>(conversationPath(uid))
    return res.data
}

export async function renameGroup(uid: string, name: string | null): Promise<void> {
    await client.put(`${conversationPath(uid)}/name`, {name})
}

export async function addPeople(uid: string, people: string[]): Promise<void> {
    await client.post(`${conversationPath(uid)}/people`, {people})
}

export async function removePerson(uid: string, personUid: string): Promise<void> {
    await client.delete(`${conversationPath(uid)}/people/${personUid}`)
}

export async function leaveConversation(uid: string): Promise<void> {
    await client.post(`${conversationPath(uid)}/leave`)
}

export async function muteConversation(uid: string, muted: boolean): Promise<void> {
    await client.put(`${conversationPath(uid)}/mute`, {muted})
}

export async function markRead(uid: string, messageId: number): Promise<void> {
    await client.put(`${conversationPath(uid)}/read`, {messageId})
}

export async function getMessages(uid: string, query: MessagePageQuery = {}): Promise<ConversationMessagePage> {
    const res = await client.get<ConversationMessagePage>(`${conversationPath(uid)}/messages`, {params: query})
    return res.data
}

export async function getMessage(uid: string, messageId: number): Promise<ConversationMessageResponse> {
    const res = await client.get<ConversationMessageResponse>(messagePath(uid, messageId))
    return res.data
}

export async function sendMessage(uid: string, text: string, replyToId: number | null): Promise<ConversationMessageResponse> {
    const res = await client.post<ConversationMessageResponse>(`${conversationPath(uid)}/messages`, {text, replyToId})
    return res.data
}

export async function editMessage(uid: string, messageId: number, text: string): Promise<ConversationMessageResponse> {
    const res = await client.put<ConversationMessageResponse>(messagePath(uid, messageId), {text})
    return res.data
}

export async function deleteMessage(uid: string, messageId: number): Promise<void> {
    await client.delete(messagePath(uid, messageId))
}

export async function react(uid: string, messageId: number, emoji: string): Promise<ConversationMessageResponse> {
    const res = await client.post<ConversationMessageResponse>(`${messagePath(uid, messageId)}/reactions`, {emoji})
    return res.data
}

export async function takeBackReaction(uid: string, messageId: number, emoji: string): Promise<ConversationMessageResponse> {
    const res = await client.delete<ConversationMessageResponse>(`${messagePath(uid, messageId)}/reactions`, {params: {emoji}})
    return res.data
}

/** The people the reader may write to: everybody sharing a station with them. */
export async function findPeople(search: string): Promise<ReachablePersonResponse[]> {
    const res = await client.get<ReachablePersonResponse[]>('/messages/people', {params: {search}})
    return res.data
}

export async function getBlocks(): Promise<MessagePersonResponse[]> {
    const res = await client.get<MessagePersonResponse[]>('/messages/blocks')
    return res.data
}

export async function block(personUid: string): Promise<void> {
    await client.post('/messages/blocks', {person: personUid})
}

export async function unblock(personUid: string): Promise<void> {
    await client.delete(`/messages/blocks/${personUid}`)
}
