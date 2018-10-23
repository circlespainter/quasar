/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2013-2014, Parallel Universe Software Co. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
 */
package co.paralleluniverse.strands;

import java.util.concurrent.Callable;

/**
 *
 * @author pron
 */
public class RunnableCallableUtils {
    public static Callable<Void> runnableToCallable(Runnable runnable) {
        return new VoidCallable(runnable);
    }

    public static class VoidCallable implements Callable<Void> {
        private final Runnable runnable;

        public VoidCallable(Runnable runnable) {
            if (runnable == null)
                throw new NullPointerException("Runnable is null");
            this.runnable = runnable;
        }

        @Override
        public Void call() {
            runnable.run();
            return null;
        }

        public Runnable getRunnable() {
            return runnable;
        }
    }
}