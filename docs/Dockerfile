FROM docker.io/library/nginx:stable-alpine

ARG VCS_REF
ARG SOURCE_URL
LABEL org.opencontainers.image.title="Glue documentation" \
      org.opencontainers.image.revision="$VCS_REF" \
      org.opencontainers.image.source="$SOURCE_URL"

RUN rm -rf /usr/share/nginx/html/*
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/ /usr/share/nginx/html/
RUN test -f /usr/share/nginx/html/index.html \
    && test -f /usr/share/nginx/html/404.html \
    && nginx -t

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/healthz || exit 1
