package nl.dflipse.fit.instrument.services;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

public class OTELCollectorService extends GenericContainer<OTELCollectorService> {
  private static final MountableFile CONFIG_FILE = MountableFile
      .forClasspathResource("otel-collector/collector-config.yaml");
  private static final MountableFile CONFIG_FILE_JAEGER = MountableFile
      .forClasspathResource("otel-collector/collector-config-jaeger.yaml");
  private static final String CONFIG_PATH = "/otel-collector-config.yaml";
  private static final String IMAGE = "otel/opentelemetry-collector-contrib:latest";

  private boolean useJeager = false;
  private boolean setup = false;

  public OTELCollectorService() {
    super(IMAGE);
    withCommand("--config=/otel-collector-config.yaml");
  }

  public OTELCollectorService withJeager() {
    useJeager = true;
    return this;
  }

  @Override
  public void start() {
    if (!setup) {
      withCopyFileToContainer(useJeager ? CONFIG_FILE_JAEGER : CONFIG_FILE, CONFIG_PATH);
      setup = true;
    }

    super.start();
  }

}
