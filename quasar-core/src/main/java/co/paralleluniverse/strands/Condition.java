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

import java.util.concurrent.TimeUnit;

/**
 *
 * @author pron
 */
public interface Condition extends Synchronization {
    @Override
    Object register();

    @Override
    void unregister(Object registrationToken);

    void await(int iter) throws InterruptedException;

    void await(int iter, long timeout, TimeUnit unit) throws InterruptedException;

    void signal();

    void signalAll();
}
