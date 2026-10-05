package ru.library.frontend;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.stage.Window;

import ru.library.Backend;
import ru.library.dto.AuthResult;
import ru.library.dto.SessionToken;
import ru.library.exception.LibraryException;
import ru.library.model.Role;
import ru.library.model.User;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class AppContext implements AutoCloseable {
    final Stage stage;
    final Backend backend;
    final SessionToken token;
    final User user;
    final Runnable signedOut;
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread thread = new Thread(r, "library-data");
                        thread.setDaemon(true);
                        return thread;
                    });
    private boolean closed;
    private long revision;
    private final Map<Section, Node> screens = new HashMap<>();
    private final Map<Node, Long> readRequests = new IdentityHashMap<>();

    private record Freshness(long revision, long fetchedAt) {}

    private final Map<Node, Freshness> freshness = new IdentityHashMap<>();

    Node screen(Section section, Supplier<Node> factory) {
        requireUiThread();
        Node screen = screens.get(section);
        if (screen == null) {
            screen = factory.get();
            screens.put(section, screen);
            freshness.put(screen, new Freshness(revision, System.nanoTime()));
        } else {
            Freshness state = freshness.get(screen);
            if (state == null
                    || state.revision() != revision
                    || System.nanoTime() - state.fetchedAt() > TimeUnit.SECONDS.toNanos(60)) {
                freshness.put(screen, new Freshness(revision, System.nanoTime()));
                Ui.refresh(screen);
            }
        }
        return screen;
    }

    void dataChanged() {
        requireUiThread();
        revision++;
    }

    private void loaded(Node owner, long requestedRevision) {
        for (Node node = owner; node != null; node = node.getParent()) {
            if (freshness.containsKey(node)) {
                freshness.put(node, new Freshness(requestedRevision, System.nanoTime()));
                return;
            }
        }
    }

    <T> void read(Node owner, Callable<T> action, Consumer<T> success) {
        read(owner, action, success, error -> Ui.error(stage, error));
    }

    <T> void read(Node owner, Callable<T> action, Consumer<T> success, Consumer<String> failure) {
        requireUiThread();
        long request = readRequests.merge(owner, 1L, Long::sum), requestedRevision = revision;
        submit(
                owner,
                false,
                action,
                value -> {
                    if (readRequests.get(owner) != request) return;
                    loaded(owner, requestedRevision);
                    success.accept(value);
                },
                error -> {
                    if (readRequests.get(owner) == request) {
                        for (Node node = owner; node != null; node = node.getParent()) {
                            if (freshness.containsKey(node)) {
                                freshness.put(node, null);
                                break;
                            }
                        }
                        failure.accept(error);
                    }
                });
    }

    AppContext(Stage stage, Backend backend, AuthResult session, Runnable signedOut) {
        this.stage = stage;
        this.backend = backend;
        this.token = session.token();
        this.user = session.user();
        this.signedOut = signedOut;
    }

    boolean admin() {
        return user.role() == Role.ADMIN;
    }

    <T> void load(Node owner, Callable<T> action, Consumer<T> success) {
        execute(
                owner,
                action,
                value -> {
                    if (owner.getScene() != null) success.accept(value);
                },
                error -> Ui.error(stage, error));
    }

    <T> void execute(
            Node owner, Callable<T> action, Consumer<T> success, Consumer<String> failure) {
        submit(owner, true, action, success, failure);
    }

    private <T> void submit(
            Node owner,
            boolean blocking,
            Callable<T> action,
            Consumer<T> success,
            Consumer<String> failure) {
        requireUiThread();
        if (closed) return;
        if (blocking) owner.setDisable(true);
        worker.submit(
                () -> {
                    T result = null;
                    Exception error = null;
                    try {
                        result = action.call();
                    } catch (Exception exception) {
                        error = exception;
                    }
                    boolean sessionExpired =
                            error instanceof LibraryException e
                                    && e.code() == LibraryException.Code.UNAUTHENTICATED;
                    if (sessionExpired) {
                        try {
                            backend.auth().currentUser(token);
                            sessionExpired = false;
                        } catch (LibraryException e) {
                            sessionExpired = e.code() == LibraryException.Code.UNAUTHENTICATED;
                        } catch (Exception ignored) {
                            sessionExpired = false;
                        }
                    }
                    T value = result;
                    Exception problem = error;
                    boolean expired = sessionExpired;
                    Platform.runLater(
                            () -> {
                                if (closed) return;
                                if (blocking) owner.setDisable(false);
                                if (problem == null) success.accept(value);
                                else if (expired) {
                                    for (var window : List.copyOf(Window.getWindows()))
                                        if (window != stage && window instanceof Stage modal)
                                            modal.close();
                                    close();
                                    signedOut.run();
                                    Ui.error(stage, "Сеанс завершён. Войдите снова.");
                                } else failure.accept(message(problem));
                            });
                });
    }

    static String message(Exception exception) {
        if (exception instanceof LibraryException) return exception.getMessage();
        if (exception instanceof IllegalArgumentException)
            return "Проверьте заполненные поля и формат чисел.";
        return "Не удалось выполнить действие. Повторите попытку.";
    }

    void logout() {
        close();
        signedOut.run();
    }

    @Override
    public void close() {
        requireUiThread();
        if (closed) return;
        closed = true;
        worker.shutdownNow();
        backend.auth().logout(token);
        screens.clear();
        freshness.clear();
        readRequests.clear();
    }

    private static void requireUiThread() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Операция интерфейса вызвана вне JavaFX-потока");
    }
}
