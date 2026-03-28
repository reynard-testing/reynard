FROM golang:1.24.4-bullseye AS builder

# Set workdir in container
WORKDIR /go/src/go.reynard.dev/instrumentation

COPY go.mod go.sum ./
RUN go mod download

COPY . .

# Build the Lambda custom runtime binary (linux/amd64, named bootstrap per Lambda convention)
RUN CGO_ENABLED=0 GOARCH=amd64 GOOS=linux go build -o bootstrap lambda-proxy/main.go

# Final minimal image; bootstrap at root so docker export outputs to ./lambda/bootstrap
FROM scratch

COPY --from=builder /go/src/go.reynard.dev/instrumentation/bootstrap /bootstrap