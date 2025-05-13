FROM golang:1.23.4-bullseye AS builder

# Set workdir in container
WORKDIR /go/src/dflipse.nl/ds-fit

COPY go.mod go.sum ./
RUN go mod download

COPY . .

# Build the project binary
RUN go build -o fit-proxy proxy/main.go

# Final minimal image
FROM debian:bullseye-slim

WORKDIR /app
COPY --from=builder /go/src/dflipse.nl/ds-fit/fit-proxy fit-proxy

ENTRYPOINT ["/app/fit-proxy"]
