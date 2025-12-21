FROM ubuntu:24.04

# Prevent interactive prompts during build
ENV DEBIAN_FRONTEND=noninteractive

# To run:
# docker build -t filibuster-baseline .
# docker run --privileged -v ${PWD}/results:/comparison/filibuster/results filibuster-baseline

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

# 1. Install software-properties-common to allow adding PPAs
RUN apt-get update && apt-get install -y --no-install-recommends \
    software-properties-common \
    && add-apt-repository ppa:deadsnakes/ppa -y \
    && apt-get update

# Setup Python3.8 Pipx and Poetry
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3.8 \
    python3.8-distutils \
    python3.8-venv \
    python3.8-dev \
    python3-pip \
    pipx

ENV PATH="/root/.local/bin:$PATH"
RUN pipx install poetry

# Clone patched Filibuster into ./filibuster
WORKDIR /comparison
RUN git clone -b track-changes --single-branch https://github.com/delanoflipse/filibuster-comparison.git filibuster
WORKDIR /comparison/filibuster
RUN poetry env use python3.8 && poetry install

# Clone patched Corpus into ./corpus
WORKDIR /comparison
RUN git clone -b baseline --single-branch https://github.com/delanoflipse/filibuster-corpus.git corpus

# Set up entrypoint
WORKDIR /comparison/filibuster
COPY docker-entrypoint.sh .
ENTRYPOINT [ "./docker-entrypoint.sh" ]