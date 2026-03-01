ARG ARCH='amd64'
ARG GRADLE_VERSION='9.3.1'
ARG JAVA_VERSION='25'

FROM ${ARCH}/gradle:${GRADLE_VERSION}-jdk${JAVA_VERSION}-graal AS build
COPY . .
RUN gradle :server:nativeCompile

FROM ${ARCH}/debian:bookworm

# Copy the binary.
COPY --from=build --chmod=755 /home/gradle/server/build/native/nativeCompile/server /sympauthy

ENTRYPOINT ["/sympauthy"]
