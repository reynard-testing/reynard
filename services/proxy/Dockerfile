FROM golang:1.23.4-bullseye
WORKDIR /go/src/dflipse.nl/fit-proxy

COPY go.mod go.sum ./
RUN go mod download

COPY . .
# ENV GOPROXY=direct
RUN go build -o fit-proxy main.go

CMD [ "./fit-proxy" ]