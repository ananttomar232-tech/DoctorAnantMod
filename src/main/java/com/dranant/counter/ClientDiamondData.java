package com.dranant.counter;

import net.minecraft.Util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Client-side mirror of the Diamond Counter (plain Java, safe to reference from common packet handlers). */
public final class ClientDiamondData {
    public static final long POPUP_MILLIS = 1800L;

    private static int total;
    private static boolean running;
    private static boolean visible;
    private static final List<Popup> POPUPS = new ArrayList<>();

    public record Popup(int amount, long startMillis, float xJitter) {}

    public static synchronized void update(int newTotal, int delta, boolean isRunning, boolean isVisible) {
        total = newTotal;
        running = isRunning;
        visible = isVisible;
        if (delta > 0) {
            POPUPS.add(new Popup(delta, Util.getMillis(), (float) (Math.random() * 30.0 - 15.0)));
            if (POPUPS.size() > 12) POPUPS.remove(0);
        }
    }

    public static synchronized int getTotal() {
        return total;
    }

    public static synchronized boolean isRunning() {
        return running;
    }

    public static synchronized boolean isVisible() {
        return visible;
    }

    /** Returns the live popups and drops expired ones. */
    public static synchronized List<Popup> getActivePopups() {
        long now = Util.getMillis();
        Iterator<Popup> it = POPUPS.iterator();
        while (it.hasNext()) {
            if (now - it.next().startMillis() > POPUP_MILLIS) it.remove();
        }
        return new ArrayList<>(POPUPS);
    }

    private ClientDiamondData() {}
}
