FROM golang:1.24.4-bullseye AS builder

# Set workdir in container
WORKDIR /go/src/go.reynard.dev/instrumentation

COPY go.mod go.sum ./
RUN go mod download

COPY . .

# Build the project binary (static, so it also runs as a Lambda extension)
RUN CGO_ENABLED=0 go build -o fit-proxy proxy/main.go

# Lambda extension layer: Lambda starts every executable in /opt/extensions
FROM scratch AS lambda-layer
COPY --from=builder /go/src/go.reynard.dev/instrumentation/fit-proxy /extensions/reynard-proxy

# Final minimal image
FROM debian:bullseye-slim

WORKDIR /app
COPY --from=builder /go/src/go.reynard.dev/instrumentation/fit-proxy fit-proxy

ENTRYPOINT ["/app/fit-proxy"]
