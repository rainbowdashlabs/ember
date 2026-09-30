/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.LinkedKeyBinding;
import dev.chojo.ember.EmberModule;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationContractCatalog;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.ExceptionHandler;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import io.javalin.router.Endpoint;
import io.javalin.router.JavalinDefaultRoutingApi;
import io.javalin.security.Roles;
import io.javalin.security.RouteRole;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsExceptionHandler;
import io.javalin.websocket.WsHandlerType;
import org.jetbrains.annotations.NotNull;
import org.mockito.invocation.InvocationOnMock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.mockito.Mockito.mock;

/**
 * The application's router as production builds it, for tests that hold every route to a rule.
 *
 * <p>The route groups come out of the application's own injector, in the order it binds them, and
 * each registers itself on a real Javalin router exactly as {@code ApiServer} has it do. Nothing is
 * read from source: a route is what the router holds, with the roles it declares, in the order it
 * answers. The injector is configured as a development instance, which is the configuration that
 * registers every route there is.
 *
 * <p>The router cannot say which method answers a route, because it holds a method reference and
 * a method reference does not name its target. Each route group is therefore built a second time
 * with test doubles for everything it depends on, its routes are registered again, and each of them
 * is asked a question with a request that refuses to answer anything. The method the refusal
 * surfaces in is the handler. The doubles are what makes that safe: nothing a handler reaches
 * before it first reads the request can write anywhere.
 */
public final class RegisteredRoutes {

    private static RegisteredRoutes application;

    private final List<Class<? extends Routes>> boundOrder;
    private final List<Route> routes;
    private final Javalin router;

    private RegisteredRoutes(List<Class<? extends Routes>> boundOrder, List<Route> routes, Javalin router) {
        this.boundOrder = boundOrder;
        this.routes = routes;
        this.router = router;
    }

    /**
     * The application's routes, built once per test run.
     *
     * @return the registered routes
     */
    public static synchronized RegisteredRoutes application() {
        if (application == null) application = build();
        return application;
    }

    /**
     * Every route, in the order the router was given them.
     *
     * @return the routes
     */
    public List<Route> routes() {
        return routes;
    }

    /**
     * The route classes in the order the application binds them, which is the order they register in.
     *
     * @return the bound route classes
     */
    public List<Class<? extends Routes>> boundOrder() {
        return boundOrder;
    }

    /**
     * The route the router answers a request with.
     *
     * @param method the verb
     * @param path   the request path
     * @return the endpoint the router picks first, if any matches
     */
    public Optional<Endpoint> answering(HandlerType method, String path) {
        return Optional.ofNullable(router.unsafe.internalRouter.findFirstHttpHandlerEntry(method, path))
                .map(parsed -> parsed.endpoint);
    }

    /**
     * Every endpoint the Javalin router holds, as it holds them.
     *
     * @return the router's endpoints
     */
    public List<Endpoint> routerEndpoints() {
        return router.unsafe.internalRouter.allHttpHandlers().stream()
                .map(parsed -> parsed.endpoint)
                .toList();
    }

    /**
     * The route classes the application binds, in the order it binds them, read from the module's
     * bindings without building anything they depend on.
     *
     * @return the bound route classes
     */
    public static List<Class<?>> boundClasses() {
        List<Class<?>> bound = new ArrayList<>();
        for (Element element : Elements.getElements(new EmberModule(new Conf(developmentConfig())))) {
            if (element instanceof LinkedKeyBinding<?> binding
                    && binding.getKey().getTypeLiteral().getRawType() == Routes.class) {
                bound.add(binding.getLinkedKey().getTypeLiteral().getRawType());
            }
        }
        return List.copyOf(bound);
    }

    private static RegisteredRoutes build() {
        Injector injector = Guice.createInjector(new EmberModule(new Conf(developmentConfig())));
        Set<Routes> bound = injector.getInstance(Key.get(new TypeLiteral<Set<Routes>>() {}));
        List<Class<? extends Routes>> order = new ArrayList<>();
        List<Route> routes = new ArrayList<>();
        Javalin router = Javalin.create(config -> {
            for (Routes group : bound) {
                order.add(group.getClass());
                List<Endpoint> registered = new ArrayList<>();
                group.register(new Recorder(config.routes, registered), RouteHarness.PREFIX);
                routes.addAll(identified(group.getClass(), registered, injector));
            }
        });
        return new RegisteredRoutes(List.copyOf(order), List.copyOf(routes), router);
    }

    /**
     * Pairs the endpoints a route group registered with the methods answering them, by registering a
     * twin of the group built from doubles and asking each of its handlers who it is.
     */
    private static List<Route> identified(Class<? extends Routes> owner, List<Endpoint> registered, Injector injector) {
        List<Endpoint> twins = new ArrayList<>();
        twinOf(owner, injector).register(new Recorder(null, twins), RouteHarness.PREFIX);
        if (twins.size() != registered.size()) {
            throw new AssertionError("%s registers %d routes when built by the injector and %d when built from doubles"
                    .formatted(owner.getSimpleName(), registered.size(), twins.size()));
        }
        List<Route> routes = new ArrayList<>();
        for (int i = 0; i < registered.size(); i++) {
            Endpoint endpoint = registered.get(i);
            Endpoint twin = twins.get(i);
            if (twin.method != endpoint.method || !twin.path.equals(endpoint.path)) {
                throw new AssertionError("%s registers %s %s where its twin registers %s %s"
                        .formatted(owner.getSimpleName(), endpoint.method, endpoint.path, twin.method, twin.path));
            }
            var frames = handlerFrames(owner, twin);
            routes.add(new Route(owner, endpoint, frames.frames(), frames.served()));
        }
        return routes;
    }

    /**
     * A route group built from doubles, with the injector's own configuration objects so that it
     * registers the same routes.
     */
    private static Routes twinOf(Class<? extends Routes> owner, Injector injector) {
        Constructor<?> constructor = injectableConstructor(owner);
        Object[] arguments = Arrays.stream(constructor.getParameters())
                .map(parameter -> doubleFor(parameter, injector))
                .toArray();
        try {
            constructor.setAccessible(true);
            return owner.cast(constructor.newInstance(arguments));
        } catch (InvocationTargetException e) {
            throw new AssertionError("Could not build " + owner.getSimpleName() + " from doubles", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not build " + owner.getSimpleName() + " from doubles", e);
        }
    }

    private static Constructor<?> injectableConstructor(Class<?> owner) {
        Constructor<?>[] constructors = owner.getDeclaredConstructors();
        return Arrays.stream(constructors)
                .filter(constructor -> constructor.isAnnotationPresent(jakarta.inject.Inject.class)
                        || constructor.isAnnotationPresent(com.google.inject.Inject.class))
                .findFirst()
                .orElseGet(() -> constructors[0]);
    }

    private static Object doubleFor(Parameter parameter, Injector injector) {
        Class<?> type = parameter.getType();
        if (type.isPrimitive()) return primitiveDefault(type);
        if (type.getPackageName().startsWith("dev.chojo.ember.conf")) return injector.getInstance(type);
        return mock(type);
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return 0;
    }

    /**
     * The methods of the route class on the way from the handler to the request that refused to be
     * read, outermost first: the first is the handler, and for a handler written inline the next is
     * the method it hands the request to.
     *
     * <p>A federation endpoint bound with {@link FederationContractBinder#serve} has no method of its
     * own in the route class: the binder answers it from the feature's serving function. Such a route
     * is named after the binder's adapter and marked as served.
     */
    private static Handling handlerFrames(Class<?> owner, Endpoint endpoint) {
        Context probe = mock(Context.class, invocation -> answerProbe(endpoint, invocation));
        try {
            endpoint.handler.handle(probe);
        } catch (Throwable thrown) {
            for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
                List<String> frames = framesOf(owner, cause);
                if (!frames.isEmpty()) return new Handling(frames, false);
            }
            for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
                List<String> frames = framesOf(FederationContractBinder.class, cause);
                if (!frames.isEmpty()) return new Handling(frames, true);
            }
        }
        throw new AssertionError("Could not tell which method of %s answers %s %s"
                .formatted(owner.getSimpleName(), endpoint.method, endpoint.path));
    }

    private static List<String> framesOf(Class<?> owner, Throwable thrown) {
        StackTraceElement[] frames = thrown.getStackTrace();
        List<String> names = new ArrayList<>();
        for (int i = frames.length - 1; i >= 0; i--) {
            if (!frames[i].getClassName().equals(owner.getName())) continue;
            String name = frames[i].getMethodName();
            if (names.isEmpty() || !names.getLast().equals(name)) names.add(name);
        }
        return names;
    }

    /**
     * A request that answers nothing except the contract headers a federation endpoint checks before
     * its handler runs, so that the check lets the probe through to the handler.
     */
    private static Object answerProbe(Endpoint endpoint, InvocationOnMock invocation) {
        if (invocation.getMethod().getName().equals("header") && invocation.getArguments().length == 1) {
            var contract = FederationContractVersions.current();
            Object name = invocation.getArgument(0);
            if (FederationHeaders.HEADER_CORE.equals(name)) return contract.core();
            if (FederationHeaders.HEADER_SURFACE.equals(name)) {
                return contractSurface(endpoint)
                        .map(surface -> contract.featureHash(surface.capability()))
                        .orElse(null);
            }
        }
        throw new Probe();
    }

    private static Optional<FederationSurface> contractSurface(Endpoint endpoint) {
        return FederationContractCatalog.ENDPOINTS.stream()
                .filter(declared -> declared.method() == endpoint.method)
                .filter(declared -> (RouteHarness.PREFIX + declared.path()).equals(endpoint.path))
                .map(FederationEndpoint::surface)
                .filter(surface -> surface != FederationSurface.CORE)
                .findFirst();
    }

    private static Path developmentConfig() {
        try {
            Path directory = Files.createTempDirectory("ember-routes");
            Files.writeString(directory.resolve("config.yaml"), """
                    auth:
                      tokenPepper: registered-routes-pepper
                    demo:
                      dev: true
                    """);
            return directory;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Handling(List<String> frames, boolean served) {}

    /**
     * One registered route and what answers it.
     *
     * @param owner    the route class that registered it
     * @param endpoint the endpoint as the router holds it
     * @param frames   the methods of the owner the request passes through, outermost first
     * @param served   whether the federation contract binder answers it from a feature's serving
     *                 function, handing that function the partner resolved from the signature
     */
    public record Route(Class<? extends Routes> owner, Endpoint endpoint, List<String> frames, boolean served) {

        /**
         * The method of the owner that answers the route, which for a handler written inline in
         * {@code register} is the compiler's name for that lambda.
         */
        public String handler() {
            return frames.getFirst();
        }

        /**
         * The method whose body does the answering: the handler itself, or for a handler written
         * inline the method it hands the request to. Empty where an inline handler reads the request
         * itself, which leaves nothing of its own to look into.
         */
        public Optional<String> body() {
            return frames.stream().filter(frame -> !frame.startsWith("lambda$")).findFirst();
        }

        /** The verb. */
        public HandlerType method() {
            return endpoint.method;
        }

        /** The full path, prefix included. */
        public String path() {
            return endpoint.path;
        }

        /** The roles the route declares. */
        public Set<RouteRole> roles() {
            Roles roles = endpoint.metadata(Roles.class);
            return roles == null ? Set.of() : roles.getRoles();
        }

        /** Whether the path takes a value from the address. */
        public boolean takesAnId() {
            return endpoint.path.contains("{") || endpoint.path.contains("<");
        }

        @Override
        public String toString() {
            return "%s %s -> %s.%s".formatted(method(), path(), owner.getSimpleName(), String.join(" -> ", frames));
        }
    }

    /** What the request a handler is identified with throws on everything it is asked. */
    private static final class Probe extends RuntimeException {
        private Probe() {
            super("identifying the handler", null, false, true);
        }
    }

    /**
     * Records every endpoint a route group registers, and hands it on to the router when there is
     * one.
     */
    private record Recorder(JavalinDefaultRoutingApi router, List<Endpoint> registered)
            implements JavalinDefaultRoutingApi {

        @Override
        public @NotNull JavalinDefaultRoutingApi addEndpoint(@NotNull Endpoint endpoint) {
            registered.add(endpoint);
            if (router != null) router.addEndpoint(endpoint);
            return this;
        }

        @Override
        public <E extends Exception> @NotNull JavalinDefaultRoutingApi exception(
                @NotNull Class<E> exceptionClass, @NotNull ExceptionHandler<? super E> exceptionHandler) {
            throw new UnsupportedOperationException("Route groups register routes, not exception handlers");
        }

        @Override
        public @NotNull JavalinDefaultRoutingApi error(
                int status, @NotNull String contentType, @NotNull Handler handler) {
            throw new UnsupportedOperationException("Route groups register routes, not error handlers");
        }

        @Override
        public <E extends Exception> @NotNull JavalinDefaultRoutingApi wsException(
                @NotNull Class<E> exceptionClass, @NotNull WsExceptionHandler<? super E> exceptionHandler) {
            throw new UnsupportedOperationException("Route groups register no websockets");
        }

        @Override
        public @NotNull JavalinDefaultRoutingApi addWsHandler(
                @NotNull WsHandlerType handlerType,
                @NotNull String path,
                @NotNull Consumer<WsConfig> wsConfig,
                @NotNull RouteRole... roles) {
            throw new UnsupportedOperationException("Route groups register no websockets");
        }
    }
}
