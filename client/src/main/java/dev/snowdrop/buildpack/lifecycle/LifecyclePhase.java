package dev.snowdrop.buildpack.lifecycle;

import dev.snowdrop.buildpack.Logger;

public interface LifecyclePhase {
    public ContainerStatus runPhase(Logger logger, boolean useTimestamps);
}
