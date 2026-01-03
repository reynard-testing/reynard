#!/bin/bash

# Set sysctl parameters to avoid running out of ephemeral ports
# Note: this is only temporary for the current session
sudo sysctl -w net.ipv4.tcp_synack_retries=2
sudo sysctl -w net.ipv4.ip_local_port_range="1024 65535"
sudo sysctl -w net.ipv4.tcp_fin_timeout=15
sudo sysctl -w net.ipv4.tcp_tw_reuse=1
echo "Adjusted sysctl settings for ephemeral ports"