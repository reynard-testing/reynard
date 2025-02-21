FROM golang:1.23.4
WORKDIR /go/src/dflipse.nl/fit-proxy
COPY . .
ENV GOPROXY=direct
RUN go build -o fit-proxy main.go

CMD [ "./fit-proxy" ]