# Stage 1: Use a Maven/JDK image to get the binaries
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build-env

# Stage 2: Build the final DinD image
FROM docker:24.0.7-dind

# Set environment variables for Java and Maven
ENV JAVA_HOME=/opt/java/openjdk
ENV MAVEN_HOME=/usr/share/maven
ENV PATH="${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${PATH}"

# Copy Java from the build-env stage
COPY --from=build-env /opt/java/openjdk /opt/java/openjdk

# Copy Maven from the build-env stage
COPY --from=build-env /usr/share/maven /usr/share/maven

# Install dependencies needed for Java (Alpine-specific)
# We add 'bash' because Maven scripts often require it
RUN apk add --no-cache libstdc++ gcompat bash git make curl

# Verify installations
RUN java -version && mvn -version

# Stage 3: prepare the application
WORKDIR /experiments

# Clone Reynard repository
RUN git clone --single-branch https://github.com/reynard-testing/reynard.git reynard

# Clone patched Filibuster Corpus with Reynard compatibility
RUN git clone -b reynard-changes --single-branch https://github.com/delanoflipse/filibuster-corpus.git benchmarks/filibuster-corpus

# Clone patched Astronomy shop with Reynard compatibility
RUN git clone -b track-changes --single-branch  https://github.com/delanoflipse/opentelemetry-demo-ds-fit.git benchmarks/astronomy-shop

# Clone patched DeathStarBench with Reynard compatibility
RUN git clone -b fit-instrumentation --single-branch https://github.com/delanoflipse/DeathStarBench.git benchmarks/DeathStarBench

# Default command remains the DinD entrypoint
ENTRYPOINT ["dockerd-entrypoint.sh"]
CMD []