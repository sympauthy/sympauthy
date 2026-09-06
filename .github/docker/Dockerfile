# The server ships as a GraalVM native image, so the base carries no runtime: the binary links
# against glibc and zlib and needs nothing else. java-base is the distroless variant that carries
# both — distroless/base ships glibc without zlib, and the binary then fails to load. It is also the
# variant to grow into should the image ever link against another library.
#
# nonroot runs the server as uid 65532. A configuration file or a database file mounted into the
# container has to be readable — and writable, for an H2 file datasource — by that user.
#
# DEBIAN_VERSION is handed in by the workflow from gradle/libs.versions.toml, where every pinned
# version lives. It deliberately has no default: a build that does not pass it names no image at all,
# rather than silently pinning a second, stale copy of the Debian release here.
ARG DEBIAN_VERSION

FROM gcr.io/distroless/java-base-debian${DEBIAN_VERSION}:nonroot

COPY --chmod=755 sympauthy /sympauthy

ENTRYPOINT ["/sympauthy"]
