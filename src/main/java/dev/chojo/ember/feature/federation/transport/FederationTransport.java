/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.transport;

import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * How the asking side talks to a partner, without knowing where the partner lives.
 *
 * <p>Every call names the asking station's own partner row and a request built from a declared
 * endpoint. The HTTP verb comes from the endpoint. A partner on another instance is sent a signed
 * request; a partner on this one is answered by the same serving function its {@code /remote}
 * route would call.
 */
public interface FederationTransport {

    /**
     * Reads one object.
     *
     * @param partner the asking station's partner row
     * @param request the request to make
     * @param type    the type to read the answer as
     * @param <T>     the answer type
     * @return the answer, never {@code null}
     */
    <T> T get(FederationPartner partner, FederationRequest request, Class<T> type);

    /**
     * Reads a list. A partner on another instance that cannot be reached answers an empty list.
     *
     * @param partner     the asking station's partner row
     * @param request     the request to make
     * @param elementType the type to read each element as
     * @param <T>         the element type
     * @return the answer
     */
    <T> List<T> getList(FederationPartner partner, FederationRequest request, Class<T> elementType);

    /**
     * Sends a body and reads the answer.
     *
     * @param partner the asking station's partner row
     * @param request the request to make
     * @param body    the body the endpoint declares, {@code null} for none
     * @param type    the type to read the answer as
     * @param <T>     the answer type
     * @return the answer, never {@code null}
     */
    <T> T send(FederationPartner partner, FederationRequest request, @Nullable Object body, Class<T> type);

    /**
     * Sends to an endpoint that answers nothing and waits until the partner took it in.
     *
     * @param partner the asking station's partner row
     * @param request the request to make
     * @param body    the body the endpoint declares, {@code null} for none
     */
    void deliver(FederationPartner partner, FederationRequest request, @Nullable Object body);

    /**
     * Sends a body and reads a list. A partner on another instance that cannot be reached answers
     * an empty list.
     *
     * @param partner     the asking station's partner row
     * @param request     the request to make
     * @param body        the body the endpoint declares
     * @param elementType the type to read each element as
     * @param <T>         the element type
     * @return the answer
     */
    <T> List<T> sendList(FederationPartner partner, FederationRequest request, Object body, Class<T> elementType);

    /**
     * Pushes a change to a partner and does not wait for it to be taken in. Nothing is pushed to a
     * partnership that is not active.
     *
     * @param partner the asking station's partner row
     * @param webhook the partner's webhook to call
     * @param body    the body the webhook declares
     */
    void notify(FederationPartner partner, FederationRequest webhook, Object body);
}
