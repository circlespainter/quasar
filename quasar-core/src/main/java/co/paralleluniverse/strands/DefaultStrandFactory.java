package co.paralleluniverse.strands;

import co.paralleluniverse.fibers.DefaultFiberScheduler;

public class DefaultStrandFactory {
    private static final StrandFactory INSTANCE =
        target -> new co.paralleluniverse.fibers.Fiber(DefaultFiberScheduler.getInstance(), target);

    public static StrandFactory getInstance() {
        return INSTANCE;
    }
}
