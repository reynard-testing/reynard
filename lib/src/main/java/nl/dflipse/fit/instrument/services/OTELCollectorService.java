package nl.dflipse.fit.instrument.services;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

public class OTELCollectorService extends GenericContainer<OTELCollectorService> {
  private static final MountableFile CONFIG_FILE = MountableFile
      .forClasspathResource("otel-collector/collector-config.yaml");
  private static final MountableFile CONFIG_FILE_JAEGER = MountableFile
      .forClasspathResource("otel-collector/collector-config-jaeger.yaml");
  private static final String IMAGE = "otel/opentelemetry-collector-contrib:latest";

  public OTELCollectorService() {
    super(IMAGE);
    this
        .withCopyFileToContainer(CONFIG_FILE, "/otel-collector-config.yaml")
        .withCommand("--config=/otel-collector-config.yaml");
  }

  public OTELCollectorService withJaeger() {
    return this.withCopyFileToContainer(CONFIG_FILE_JAEGER, "/otel-collector-config.yaml");
  }

}
