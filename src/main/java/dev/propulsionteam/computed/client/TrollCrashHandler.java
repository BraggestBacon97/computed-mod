package dev.propulsionteam.computed.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

/**
 * Turns the occasional Create/Registrate "Found unused register callbacks" crash
 * (a flaky parallel-dispatch race, dev-environment only) into something
 * considerably more entertaining. The original exception is preserved as the
 * cause, so no diagnostic information is lost - Bacon just gets a better story.
 */
public final class TrollCrashHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String[] TROLL_MESSAGES = {
        "Bacon ist ein Femboy :3 - der Registrate-Crash war reine Cuteness-Overload",
        "Create hat die Register-Callbacks verschluckt und hält sie jetzt als Haustier",
        "Pwörk! Das war kein Fehler, das war der zufällige Bacon-Bypass",
        "Diese Mods passen nicht zusammen? Doch! Bacon hat sie geheiratet",
        "Exception in thread \"modloading-sync-worker\": uwu-Mismatch am Port 8080",
        "Bacon hat zu viele Thigh-Highs an - StackOverflow vor dem Booten",
        "Boolean braucht keinen Crash; er wollte nur Vorspielen, wie er Errors sehen würde",
        "Random-Race im Registry-Freeze: diesmal gewinnt der Femboy",
        "subVelocity = (uwu, uwu, uwu) - Treiber meldet Cuteness-Overload",
        "Create und NeoForge streiten sich um die Callbacks, Bacon schlichtet - danach einfach neu starten",
        "Scroll nicht weiter - der Loader ist nur dezent verschämt, ein Neustart hilft UwU",
        "trollException: 'Found unused register callbacks' betrifft Create, nicht Computed - es ist nur der Zufall",
        "Registrate wollte nur kurz woanders hin - bitte noch einmal starten, er kommt gleich",
        "Der PC war eifersüchtig auf deine schönen Drone-Builds - dieser Screenshot ist der Beweis"
    };

    private static final StackTraceElement[] TROLL_STACK = {
        new StackTraceElement("dev.propulsionteam.computed.troll", "main", "CutenessOverload.java", 7),
        new StackTraceElement("com.simibubi.create.Create", "onRegister", "Create.java", 6),
        new StackTraceElement("com.tterrag.registrate.AbstractRegistrate", "onRegister", "AbstractRegistrate.java", 262),
        new StackTraceElement("net.neoforged.bus", "post", "EventBus.java", 360),
        new StackTraceElement("net.neoforged.neoforge.registries.GameData", "postRegisterEvents", "GameData.java", 92),
        new StackTraceElement("dev.propulsionteam.computed.troll", "BaconRng", "roll", 3),
        new StackTraceElement("java.base/java.lang.Thread", "run", "Thread.java", 1583)
    };

    private static volatile boolean installed = false;

    private TrollCrashHandler() {
    }

    /**
     * Chains a troll handler in front of the current default uncaught exception
     * handler. Must be called early (from the mod constructor), which runs before
     * the registry freeze that triggers the flaky Create crash.
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Throwable handled = throwable;
            try {
                if (isRegistrateCrash(throwable)) {
                    handled = new BaconTrollCrash(throwable);
                    LOGGER.error("TROLL DETECTED: Ein Registrate-Zufalls-Crash wurde in einen Bacon-Troll verwandelt.");
                }
            } catch (Throwable swallow) {
                handled = throwable;
            }
            if (previous != null) {
                previous.uncaughtException(thread, handled);
            } else {
                handled.printStackTrace();
            }
        });
    }

    private static boolean isRegistrateCrash(Throwable throwable) {
        Throwable cursor = throwable;
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (cursor != null && seen.add(cursor)) {
            String message = cursor.getMessage();
            if (message != null && message.contains("unused register callbacks")) {
                return true;
            }
            StackTraceElement[] stack = cursor.getStackTrace();
            if (stack != null) {
                for (StackTraceElement frame : stack) {
                    String className = frame.getClassName();
                    if (className != null && className.startsWith("com.tterrag.registrate.")) {
                        return true;
                    }
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static String randomMessage() {
        long seed = System.nanoTime() ^ Thread.currentThread().getId();
        return TROLL_MESSAGES[(int) (Math.floorMod(seed, TROLL_MESSAGES.length))];
    }

    private static final class BaconTrollCrash extends RuntimeException {
        private BaconTrollCrash(Throwable cause) {
            super("Exception caught during firing event: " + randomMessage(), cause);
            setStackTrace(TROLL_STACK);
        }
    }
}