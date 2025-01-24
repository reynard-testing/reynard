package nl.dflipse.fit.instrument.services;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.MountableFile;

public class OTELCollectorService implements InstrumentedService {
  public GenericContainer<?> container;
  private String name;
  private MountableFile configFile;

  private static String image = "otel/opentelemetry-collector-contrib:latest";

  public OTELCollectorService(String name, Network network) {
    this.name = name;

    configFile = MountableFile.forHostPath("../services/otel-collector/collector-config.yaml");

    this.container = new GenericContainer<>(image)
        .withCopyFileToContainer(configFile, "/otel-collector-config.yaml")
        .withCommand("--config=/otel-collector-config.yaml")
        .withNetwork(network)
        .withNetworkAliases(name);
  }

  public GenericContainer<?> getContainer() {
    return container;
  }

  public String getName() {
    return name;
  }

  public boolean isRunning() {
    return container.isRunning();
  }

  public void start() {
    container.start();
  }

  public void stop() {
    if (container != null && container.isRunning()) {
      container.stop();
    }
  }
}
