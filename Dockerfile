FROM jelovirt/kuhnuri_dita-ot:3.3.2

# RUN mkdir -p /opt/app/conf && \
#     mkdir -p /opt/app/lib
# COPY target/universal/stage/conf /opt/app/conf
COPY build/libs /opt/app/lib

COPY docker/run.sh /opt/app/run.sh
RUN chmod 755 /opt/app/run.sh

# EXPOSE 9000
# VOLUME ["/var/log/app", "/tmp/app", "/var/lib/app"]
# HEALTHCHECK CMD curl http://localhost:9000/health || exit 1

WORKDIR /opt/app
ENTRYPOINT ["./run.sh"]
