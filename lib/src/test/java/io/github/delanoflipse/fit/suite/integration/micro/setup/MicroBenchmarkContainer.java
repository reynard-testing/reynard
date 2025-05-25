package io.github.delanoflipse.fit.suite.integration.micro.setup;

import java.nio.file.Path;
import java.util.List;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.delanoflipse.fit.suite.strategy.util.Env;

@SuppressWarnings("resource")
public class MicroBenchmarkContainer extends GenericContainer<MicroBenchmarkContainer> {
    private static final String LOG_LEVEL;
    private static final String IMAGE_BUILD_CONTEXT;
    private static final Path IMAGE_PATH;
    private static final ObjectMapper mapper = new ObjectMapper();

    public enum ServiceType {
        LEAF("leaf"),
        PARALLEL("parallel"),
        PASS_THROUGH("pass-through"),
        COMPLEX("complex");

        private final String serviceName;

        ServiceType(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getServiceName() {
            return serviceName;
        }
    }

    static {
        LOG_LEVEL = Env.getEnv("LOG_LEVEL", "info");
        Path projectRootPath = Path.of("../").toAbsolutePath();
        IMAGE_BUILD_CONTEXT = ".";
        IMAGE_PATH = projectRootPath.resolve("util/micro-benchmarks/services/").toAbsolutePath();
    }

    public static MicroBenchmarkContainer Leaf() {
        return new MicroBenchmarkContainer(ServiceType.LEAF);
    }

    public static MicroBenchmarkContainer Parallel(List<String> requests, String fallbackValue) {
        return new MicroBenchmarkContainer(ServiceType.PARALLEL)
                .withEnv("FALLBACK_VALUE", fallbackValue)
                .withEnv("TARGET_SERVICE_URLS", String.join(",", requests));
    }

    public static MicroBenchmarkContainer Parallel(List<String> requests) {
        return Parallel(requests, "");
    }

    public static MicroBenchmarkContainer PassThrough(String targetServiceUrl, String fallbackValue) {
        return new MicroBenchmarkContainer(ServiceType.PASS_THROUGH)
                .withEnv("FALLBACK_VALUE", fallbackValue)
                .withEnv("TARGET_SERVICE_URL", targetServiceUrl);
    }

    public static MicroBenchmarkContainer PassThrough(String targetServiceUrl) {
        return PassThrough(targetServiceUrl, "");
    }

    public static String call(String serviceName) {
        return String.format("http://%s:8080/", serviceName);
    }

    public static MicroBenchmarkContainer Complex(Object action) {
        String actionJson = mapper.valueToTree(action).toString();
        return new MicroBenchmarkContainer(ServiceType.COMPLEX)
                .withCopyToContainer(Transferable.of(actionJson), "/action.json");
    }

    public MicroBenchmarkContainer(ServiceType serviceType) {
        super(new ImageFromDockerfile()
                .withFileFromPath(IMAGE_BUILD_CONTEXT, IMAGE_PATH)
                .withBuildArg("SERVICE_NAME", serviceType.getServiceName()));
        withExposedPorts(8080);
        withEnv("LOG_LEVEL", LOG_LEVEL);
    }

}
