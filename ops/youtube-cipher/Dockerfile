# Upstream yt-cipher revision 1e1fd8e2f34ca90cf23545be72e46307bd3d3d2a.
# Static BusyBox supplies an HTTP readiness probe for the distroless image.
FROM busybox:1.37.0-musl@sha256:5cec3fc171c87218698e85a52af7087de727372aae264a787b8112901a5b0092 AS healthcheck

FROM ghcr.io/kikkia/yt-cipher@sha256:4c5ec381ff57336cfc24822d2b526a5ad7cd0171f61065a1bbc87bb736842449

COPY --from=healthcheck /bin/busybox /usr/local/bin/busybox

HEALTHCHECK --interval=5s --timeout=3s --start-period=10s --retries=20 \
    CMD ["/usr/local/bin/busybox", "wget", "-Y", "off", "-q", "-T", "2", "-O", "/dev/null", "http://127.0.0.1:8001/metrics"]
