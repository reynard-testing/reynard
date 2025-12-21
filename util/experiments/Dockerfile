FROM ubuntu:24.04

# Prevent interactive prompts during build
ENV DEBIAN_FRONTEND=noninteractive

# To run:
# docker build -t reynard-experiments .
# docker run --privileged -v ${PWD}/results:/experiments/reynard/results reynard-experiments

# Update and install basic dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    git \
    make \
    gnupg \
    lsb-release \
    iptables \
    && rm -rf /var/lib/apt/lists/*

# Install Docker (required for Docker-in-Docker)
RUN install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg && \
    chmod a+r /etc/apt/keyrings/docker.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
    $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null && \
    apt-get update && apt-get install -y --no-install-recommends \
    docker-ce \
    docker-ce-cli \
    containerd.io \
    docker-buildx-plugin \
    docker-compose-plugin \
    && rm -rf /var/lib/apt/lists/*

# Configure Docker-in-Docker environment
VOLUME /var/lib/docker

# Install Java 17 and Maven
RUN apt-get update && apt-get install -y --no-install-recommends \
    software-properties-common \
    openjdk-17-jdk \
    maven 

# Clone and build Reynard
WORKDIR /experiments
RUN git clone https://github.com/reynard-testing/reynard.git reynard
WORKDIR /experiments/benchmarks
# Clone Filibuster Corpus
RUN git clone -b reynard-changes --single-branch https://github.com/delanoflipse/filibuster-corpus.git filibuster-corpus
# Clone Astronomy Shop
RUN git clone -b track-changes --single-branch https://github.com/delanoflipse/opentelemetry-demo-ds-fit.git astronomy-shop
# Clone DeathStarBench
RUN git clone -b fit-instrumentation --single-branch https://github.com/delanoflipse/DeathStarBench.git DeathStarBench


# Set up entrypoint
WORKDIR /experiments/reynard
COPY docker-entrypoint.sh .
ENTRYPOINT [ "./docker-entrypoint.sh" ]