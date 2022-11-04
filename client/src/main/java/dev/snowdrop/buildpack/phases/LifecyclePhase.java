package dev.snowdrop.buildpack.phases;

import dev.snowdrop.buildpack.Logger;

public interface LifecyclePhase {
    public ContainerStatus runPhase(Logger logger, boolean useTimestamps);
}
