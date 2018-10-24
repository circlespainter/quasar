package co.paralleluniverse.fibers;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DefaultFiberScheduler {
    private static final Executor INSTANCE = Executors.newWorkStealingPool();

    public static Executor getInstance() {
        return INSTANCE;
    }
}
