ARG ARCH='amd64'
ARG GRADLE_VERSION='8.6'
ARG JAVA_VERSION='25'

FROM ${ARCH}/gradle:${GRADLE_VERSION}-jdk${JAVA_VERSION}-graal AS build
COPY . .
RUN gradle :server:nativeCompile

FROM ${ARCH}/debian:bookworm

# Copy the binary.
COPY --from=build --chmod=755 /home/gradle/server/build/native/nativeCompile/server /sympauthy

# Copy the migration scripts for all supported databases.
COPY --from=build /home/gradle/databases /databases

ENTRYPOINT ["/sympauthy"]
