FROM golang:1.12.7 AS builder
WORKDIR $GOPATH/src/github.com/kuhnuri/batch-fop
RUN go get -v -u github.com/kuhnuri/go-worker
COPY docker/main.go .
#RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o app .
RUN go build -a -o main .

FROM jelovirt/kuhnuri_dita-ot:3.3.2
WORKDIR /opt/app
COPY docker/logback.xml /opt/app/config/logback.xml
COPY --from=builder /go/src/github.com/kuhnuri/batch-fop/main .
ENTRYPOINT ["./main"]
