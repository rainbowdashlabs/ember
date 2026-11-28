/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-to-server lending endpoints served to federation partners, through the serving functions
 * of {@code LendingService}. Requests carry an RSA-signed envelope instead of a user session; the
 * consumer side lives in {@link FederatedLendingRoutes} and {@link LendingRoutes}.
 *
 * <p>A request between two instances is written down on both of them and addressed by the identity
 * both copies carry, never by either instance's own row number.
 */
@Singleton
public class RemoteLendingRoutes implements Routes {

    public static final FederationEndpoint GET_AVAILABLE = FederationEndpoint.get(
            FederationSurface.INVENTORY_LEND, "/remote/lending/available", RemoteAvailability.class);
    public static final FederationEndpoint GET_PRODUCTS =
            FederationEndpoint.get(FederationSurface.INVENTORY_LEND, "/remote/lending/products", RemoteProducts.class);
    public static final FederationEndpoint GET_PRODUCT = FederationEndpoint.get(
            FederationSurface.INVENTORY_LEND, "/remote/lending/products/{inventoryId}", RemoteProduct.class);
    public static final FederationEndpoint GET_PRODUCT_DAYS = FederationEndpoint.get(
            FederationSurface.INVENTORY_LEND, "/remote/lending/products/{inventoryId}/days", RemoteProductDays.class);
    public static final FederationEndpoint CREATE_REQUEST = FederationEndpoint.post(
            FederationSurface.INVENTORY_LEND,
            "/remote/lending/requests",
            RemoteLendingRequest.class,
            RemoteLendingAccepted.class);
    public static final FederationEndpoint CHANGE_STATUS = FederationEndpoint.post(
                    FederationSurface.INVENTORY_LEND,
                    "/remote/lending/requests/{requestUid}/status",
                    RemoteLendingStatus.class,
                    Void.class)
            .keepingInStep();
    public static final FederationEndpoint GET_PICTURE = FederationEndpoint.get(
            FederationSurface.INVENTORY_LEND, "/remote/lending/pictures/{pictureKey}", RemoteLendingPicture.class);
    public static final FederationEndpoint GET_MESSAGES = FederationEndpoint.getList(
            FederationSurface.INVENTORY_LEND, "/remote/lending/requests/{requestUid}/messages", LendingMessage.class);
    public static final FederationEndpoint MESSAGE_NOTICE = FederationEndpoint.post(
                    FederationSurface.INVENTORY_LEND,
                    "/remote/lending/requests/{requestUid}/notices",
                    RemoteLendingNotice.class,
                    Void.class)
            .keepingInStep();

    public static final List<FederationEndpoint> CONTRACT = List.of(
            GET_AVAILABLE,
            GET_PRODUCTS,
            GET_PRODUCT,
            GET_PRODUCT_DAYS,
            GET_PICTURE,
            CREATE_REQUEST,
            CHANGE_STATUS,
            GET_MESSAGES,
            MESSAGE_NOTICE);

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteLendingRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, endpoints, binder -> binder.serve(GET_AVAILABLE)
                .serve(GET_PRODUCTS)
                .serve(GET_PRODUCT)
                .serve(GET_PRODUCT_DAYS)
                .serve(GET_PICTURE)
                .serveCreated(CREATE_REQUEST)
                .serve(CHANGE_STATUS)
                .serve(GET_MESSAGES)
                .serve(MESSAGE_NOTICE));
    }

    /**
     * What a station offers the asking partner, one product per inventory.
     *
     * @param products       the inventories with something on offer, best search match first
     * @param offersAnything whether the station offers the partner anything at all, which distinguishes
     *                       "nothing is shared with you" from "nothing is free just now"
     */
    public record RemoteProducts(List<RemoteProductSummary> products, boolean offersAnything) {}

    /**
     * One inventory as a tile of the lending catalog.
     *
     * @param inventoryId  the inventory
     * @param name         what the station calls it
     * @param teaser       the start of its description without Markdown, empty where there is none
     * @param pictureKey   the key of its photo, to ask {@link #GET_PICTURE} for, or {@code null}
     * @param icon         the icon it is drawn with, or {@code null}
     * @param color        the colour of that icon, or {@code null}
     * @param kinds        how many count rows it offers: its kinds, or one for an inventory of copies
     * @param pieces       how many single pieces it offers, the pieces without a kind of an inventory
     *                     of different things
     * @param free         how many pieces are free in the days asked for, today's where none were
     * @param matched      the names of the kinds or pieces a search reached it through, empty where it
     *                     matched itself or nothing was searched for
     * @param rank         how well it matched the search, higher is better, {@code 0} without a search;
     *                     every station ranks by the same rule, so ranks compare across stations
     */
    public record RemoteProductSummary(
            int inventoryId,
            String name,
            String teaser,
            @Nullable String pictureKey,
            @Nullable String icon,
            @Nullable String color,
            int kinds,
            int pieces,
            int free,
            List<String> matched,
            double rank) {}

    /**
     * One inventory as the product page shows it. Pieces with a kind are counted per kind; in an
     * inventory of different things the pieces without a kind are listed one by one; an inventory of
     * copies is one count row without a kind.
     *
     * @param inventoryId the inventory
     * @param name        what the station calls it
     * @param description its description in Markdown, empty where there is none
     * @param pictureKey  the key of its photo, or {@code null}
     * @param icon        the icon it is drawn with, or {@code null}
     * @param color       the colour of that icon, or {@code null}
     * @param address     where the gear is kept, or {@code null} where the station gives no address
     * @param kinds       the count rows on offer, with their free counts
     * @param pieces      the single pieces on offer, each free or not in the days asked for
     */
    public record RemoteProduct(
            int inventoryId,
            String name,
            String description,
            @Nullable String pictureKey,
            @Nullable String icon,
            @Nullable String color,
            @Nullable RemoteAddress address,
            List<RemoteKind> kinds,
            List<RemotePiece> pieces) {

        /** The single piece of this product with that id, where it lists one. */
        public Optional<RemotePiece> piece(int itemId) {
            return pieces.stream().filter(piece -> piece.itemId() == itemId).findFirst();
        }

        /** The count row of this product for that kind, the one without kind for {@code null}. */
        public Optional<RemoteKind> kind(@Nullable Integer artId) {
            return kinds.stream()
                    .filter(kind -> Objects.equals(kind.artId(), artId))
                    .findFirst();
        }
    }

    /**
     * Where a station keeps what it lends, as its settings give it.
     *
     * @param addressLine street and number
     * @param postalCode  the postal code
     * @param city        the city
     * @param country     the country
     */
    public record RemoteAddress(String addressLine, String postalCode, String city, String country) {}

    /**
     * One kind of a product with what is free of it.
     *
     * @param artId       the kind, or {@code null} for the one count row of an inventory of copies
     * @param name        what it is called, empty for that row
     * @param description its description in Markdown, empty where there is none
     * @param pictureKey  the key of its photo, the inventory's where it has none, or {@code null}
     * @param icon        the icon it is drawn with, or {@code null}
     * @param color       the colour of that icon, or {@code null}
     * @param free        how many are free in the days asked for, today's where none were
     */
    public record RemoteKind(
            @Nullable Integer artId,
            String name,
            String description,
            @Nullable String pictureKey,
            @Nullable String icon,
            @Nullable String color,
            int free) {}

    /**
     * One single piece of a product: a piece without a kind in an inventory of different things.
     *
     * @param itemId      the piece
     * @param name        what it is called
     * @param description its description in Markdown, empty where there is none
     * @param pictureKey  the key of the photo it is shown with: its own or its inventory's, or
     *                    {@code null}
     * @param free        whether it is free in the days asked for, today where none were
     */
    public record RemotePiece(
            int itemId,
            String name,
            String description,
            @Nullable String pictureKey,
            boolean free) {}

    /**
     * What is free of a product on each day of a month.
     *
     * @param days one entry per day of the month, in order
     */
    public record RemoteProductDays(List<RemoteDay> days) {}

    /**
     * One day of a product's calendar.
     *
     * @param day     the day
     * @param blocked whether the station lends none of it that day
     * @param counts  per kind, or for the one piece asked about, what is free and what is offered
     */
    public record RemoteDay(LocalDate day, boolean blocked, List<RemoteDayCount> counts) {}

    /**
     * What is free of one kind or one piece on a day.
     *
     * @param artId   the kind, or {@code null} for the count row of an inventory of copies, the single
     *                pieces or one piece
     * @param itemId  the piece asked about, or {@code null}
     * @param singles whether the row counts the single pieces of the product together
     * @param free    how many are free that day, never below zero
     * @param offered how many are on offer at all
     */
    public record RemoteDayCount(
            @Nullable Integer artId, @Nullable Integer itemId, boolean singles, int free, int offered) {}

    /**
     * What a station offers the asking partner.
     *
     * @param entries        what is free, counted per inventory and kind of thing
     * @param offersAnything whether the station offers the partner anything at all, which distinguishes
     *                       "nothing is shared with you" from "nothing is free just now"
     */
    public record RemoteAvailability(List<RemoteAvailableEntry> entries, boolean offersAnything) {}

    /**
     * One thing a station offers, counted.
     *
     * <p>It is shown with its kind's photo and icon where the kind has them, otherwise its inventory's.
     *
     * @param artId       the kind counted, or {@code null} where the row counts a whole inventory
     * @param artName     what that kind is called, or {@code null}
     * @param pictureKey  the key of the photo it is shown with, to ask {@link #GET_PICTURE} for, or
     *                    {@code null} for none
     * @param icon        the icon it is drawn with, or {@code null} for the plain shape
     * @param color       the colour of that icon, or {@code null}
     * @param homogeneous whether the inventory holds one thing in many copies, which decides the plain shape
     */
    public record RemoteAvailableEntry(
            int inventoryId,
            String inventoryName,
            @Nullable Integer artId,
            @Nullable String artName,
            int availableCount,
            @Nullable String pictureKey,
            @Nullable String icon,
            @Nullable String color,
            boolean homogeneous) {}

    /**
     * A photo of offered gear. The contract carries only JSON, so the picture travels encoded in it, drawn
     * small by the lending station before it leaves.
     *
     * @param mimeType the type of the picture
     * @param base64   the picture, Base64 encoded
     */
    public record RemoteLendingPicture(String mimeType, String base64) {}

    /**
     * A request for gear, as the borrowing station sends it to the lending one.
     *
     * @param uid      the identity both copies of the request carry
     * @param dateFrom the first day the gear is wanted; a request without one is refused
     * @param dateTo   the last day, {@code null} from an instance that still sends none for one day
     * @param occasion what the request is for, a copy of the appointment's name
     * @param lines    what is asked for, each line naming gear of the lending station
     */
    public record RemoteLendingRequest(
            UUID uid,
            @Nullable LocalDate dateFrom,
            @Nullable LocalDate dateTo,
            String occasion,
            List<RemoteLendingLine> lines) {}

    /**
     * One line of a request.
     *
     * @param inventoryId the inventory the line draws from, or {@code null}
     * @param itemId      the piece the line names, or {@code null}
     * @param artId       the kind of thing the line asks for, or {@code null}
     */
    public record RemoteLendingLine(
            @Nullable Integer inventoryId,
            @Nullable Integer itemId,
            @Nullable Integer artId,
            int quantity) {}

    /**
     * The lending station's answer to a request it wrote down.
     *
     * @param labels what it calls each line, in line order, for the borrowing station to show
     */
    public record RemoteLendingAccepted(List<String> labels) {}

    /**
     * A request moved on by one of the two stations.
     *
     * @param status the state it moved to
     * @param reason why, where it was declined, or {@code null}
     * @param pieces what the lending station handed over, where it moved to lent; empty otherwise and
     *               from a station that does not list them
     */
    public record RemoteLendingStatus(
            LendingStatus status,
            @Nullable String reason,
            @Nullable List<RemoteLendingPiece> pieces) {

        /** A move that hands nothing over. */
        public RemoteLendingStatus(LendingStatus status, @Nullable String reason) {
            this(status, reason, List.of());
        }

        /**
         * What was handed over, empty where nothing was listed.
         *
         * @return the pieces
         */
        public List<RemoteLendingPiece> handedOver() {
            return pieces == null ? List.of() : pieces;
        }
    }

    /**
     * One piece the lending station handed over, which the borrowing station writes down as a
     * borrowed copy.
     *
     * @param line       the position of the line it was lent on, counted from 0 in line order, which
     *                   both copies of a request share
     * @param internalId the piece's identifier, or {@code null}
     * @param name       the piece's name, or {@code null} where the lending station sent none
     */
    public record RemoteLendingPiece(
            int line, @Nullable String internalId, @Nullable String name) {}

    /**
     * Word that a message was written on a request, so the other station's managers hear of it.
     *
     * @param senderName who wrote it
     */
    public record RemoteLendingNotice(String senderName) {}
}
