package co.paralleluniverse.fibers;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DefaultFiberScheduler {
    private static final ExecutorService INSTANCE = Executors.newWorkStealingPool();

    public static ExecutorService getInstance() {
        return INSTANCE;
    }
}
