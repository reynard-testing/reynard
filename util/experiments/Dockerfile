FROM ubuntu:24.04

# To run:
# docker build -t reynard-experiments .
# docker run --privileged -v ./results:/results reynard-experiments
# (Privileged flag is needed for Docker-in-Docker)

# Prevent interactive prompts during build
ENV DEBIAN_FRONTEND=noninteractive
# Update and install basic dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    git \
    make \
    gnupg \
    lsb-release \
    iptables

# Install Docker (Docker-in-Docker)
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
    docker-compose-plugin

# Install Java 17 and Maven
RUN apt-get update && apt-get install -y --no-install-recommends \
    software-properties-common \
    openjdk-17-jdk \
    maven 

# --- Clone and build Reynard ---
WORKDIR /experiments
RUN git clone https://github.com/reynard-testing/reynard.git reynard
RUN cd reynard && make install

# --- Clone Benchmarks ---
WORKDIR /experiments/benchmarks
# Clone Filibuster Corpus
RUN git clone -b reynard-changes --single-branch https://github.com/delanoflipse/filibuster-corpus.git filibuster-corpus
# Clone Astronomy Shop
RUN git clone -b track-changes --single-branch https://github.com/delanoflipse/opentelemetry-demo-ds-fit.git astronomy-shop
# Clone DeathStarBench
RUN git clone -b fit-instrumentation --single-branch https://github.com/delanoflipse/DeathStarBench.git DeathStarBench

RUN ln -s $HOME/.docker/run/docker.sock /var/run/docker.sock
RUN mkdir -p /results

# Set up entrypoint
WORKDIR /experiments/reynard
ENTRYPOINT [ "/experiments/docker-entrypoint.sh" ]