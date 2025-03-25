package dev.snowdrop.buildpack.lifecycle;

public interface LifecyclePhaseAnalyzedTomlUpdater {
    public byte[] getAnalyzedToml();
    public void updateAnalyzedToml(String toml);
}
