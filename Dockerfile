FROM golang:1.12.12 AS builder
WORKDIR $GOPATH/src/github.com/kuhnuri/batch-dita-ot
RUN go get -v -u github.com/kuhnuri/go-worker
COPY docker/main.go .
#RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o app .
RUN go build -a -o main .

FROM docker.pkg.github.com/dita-ot/dita-ot/dita-ot:3.4
WORKDIR /opt/app
COPY docker/logback.xml /opt/app/config/logback.xml
COPY --from=builder /go/src/github.com/kuhnuri/batch-dita-ot/main .
ENTRYPOINT ["./main"]
