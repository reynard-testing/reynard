FROM golang:1.24.4-bullseye AS builder

# Set workdir in container
WORKDIR /go/src/go.reynard.dev/instrumentation

COPY go.mod go.sum ./
RUN go mod download

COPY . .

# Build the Lambda custom runtime binary (linux/amd64, named bootstrap per Lambda convention)
RUN GOARCH=amd64 GOOS=linux go build -o bootstrap lambda-extension/main.go

# Final minimal image; bootstrap is placed at /var/task/ per Lambda custom runtime convention
FROM debian:bullseye-slim

COPY --from=builder /go/src/go.reynard.dev/instrumentation/bootstrap /var/task/bootstrap
