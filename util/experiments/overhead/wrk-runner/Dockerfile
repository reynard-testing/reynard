# Use a lightweight base image
FROM alpine:3.19

# Install wrk and ca-certificates (for HTTPS support)
# --no-cache ensures we don't keep the package index, keeping the image small
RUN apk add --no-cache wrk ca-certificates

WORKDIR /opt

COPY ./scripts .

# Default command (optional help text if no args provided)
CMD ["/usr/bin/wrk", "--help"]