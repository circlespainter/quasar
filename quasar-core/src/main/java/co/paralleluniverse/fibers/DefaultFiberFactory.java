package co.paralleluniverse.fibers;

import java.util.concurrent.Callable;

public class DefaultFiberFactory {
    private static final FiberFactory INSTANCE = new FiberFactory() {
        @Override
        public <T> Fiber<T> newFiber(Callable<T> target) {
            return new Fiber<>(DefaultFiberScheduler.getInstance(), target);
        }
    };

    public static FiberFactory getInstance() {
        return INSTANCE;
    }
}
