package net.lasertag;

import android.util.Log;

import net.lasertag.model.WirelessMessage;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class EventLoopHandler {

    private enum Kind {
        DEVICE_INPUT,
        SERVER_INPUT,
        TIMER_TICK,
        ACTIVITY_SIGNAL
    }

    private static final class Event {
        final Kind kind;
        final WirelessMessage message; // for device/server inputs
        final String activityAction;   // for ACTIVITY_SIGNAL

        private Event(Kind kind, WirelessMessage message, String activityAction) {
            this.kind = kind;
            this.message = message;
            this.activityAction = activityAction;
        }

        static Event device(WirelessMessage message) {
            return new Event(Kind.DEVICE_INPUT, message, null);
        }

        static Event server(WirelessMessage message) {
            return new Event(Kind.SERVER_INPUT, message, null);
        }

        static Event timerTick() {
            return new Event(Kind.TIMER_TICK, null, null);
        }

        static Event activity(String action) {
            return new Event(Kind.ACTIVITY_SIGNAL, null, action);
        }
    }

    private final String logTag;
    private final Consumer<WirelessMessage> onDeviceMessage;
    private final Consumer<WirelessMessage> onServerMessage;
    private final Runnable onTimerTick;
    private final Consumer<String> onActivitySignal;

    // Timer scheduling only.
    private final ScheduledExecutorService timerExecutor;
    // Single-thread consumer loop.
    private ExecutorService loopExecutor;

    public EventLoopHandler(
            String logTag,
            Consumer<WirelessMessage> onDeviceMessage,
            Consumer<WirelessMessage> onServerMessage,
            Runnable onTimerTick,
            Consumer<String> onActivitySignal) {
        this.logTag = Objects.requireNonNull(logTag);
        this.onDeviceMessage = Objects.requireNonNull(onDeviceMessage);
        this.onServerMessage = Objects.requireNonNull(onServerMessage);
        this.onTimerTick = Objects.requireNonNull(onTimerTick);
        this.onActivitySignal = Objects.requireNonNull(onActivitySignal);
        this.timerExecutor = Executors.newScheduledThreadPool(1);
    }

    public void start() {
        loopExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "GameServiceGameLoop");
            t.setDaemon(true);
            return t;
        });
        timerExecutor.scheduleWithFixedDelay(this::enqueueTimerTick, 1, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        timerExecutor.shutdownNow();
        if (loopExecutor != null) {
            loopExecutor.shutdownNow();
            loopExecutor = null;
        }
    }

    public void enqueueDevice(WirelessMessage message) {
        enqueue(Event.device(message));
    }

    public void enqueueServer(WirelessMessage message) {
        enqueue(Event.server(message));
    }

    public void enqueueActivity(String action) {
        enqueue(Event.activity(action));
    }

    public void enqueueTimerTick() {
        enqueue(Event.timerTick());
    }

    public void post(Runnable runnable) {
        if (loopExecutor == null) {
            return;
        }
        try {
            loopExecutor.execute(runnable);
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void enqueue(Event event) {
        if (loopExecutor == null) {
            return;
        }
        try {
            loopExecutor.execute(() -> dispatch(event));
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void dispatch(Event event) {
        switch (event.kind) {
            case DEVICE_INPUT -> onDeviceMessage.accept(event.message);
            case SERVER_INPUT -> onServerMessage.accept(event.message);
            case TIMER_TICK -> onTimerTick.run();
            case ACTIVITY_SIGNAL -> onActivitySignal.accept(event.activityAction);
        }
    }
}

