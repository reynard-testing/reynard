FROM golang:1.23.4
WORKDIR /go/src/dflipse.nl/fit-proxy
COPY . .
RUN go build -o proxy proxy.go

CMD [ "./proxy" ]