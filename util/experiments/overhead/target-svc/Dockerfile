FROM golang:1.23.4-bullseye
WORKDIR /go/src/ok-server

COPY go.mod ./
# COPY go.sum ./
RUN go mod download

COPY . .
# ENV GOPROXY=direct
RUN go build -o ok-server main.go

CMD [ "./ok-server" ]
