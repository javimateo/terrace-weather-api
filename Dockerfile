# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && mv target/*.jar app.jar

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER app
EXPOSE 8080
# Memory tuned for a small VPS shared with other containers (~180 MiB resident instead of ~250):
# the working set is < 30 MiB of heap, so cap it instead of letting the JVM size itself from the
# host's RAM. Serial GC + C1-only JIT trade peak throughput (irrelevant at 60 req/min per IP) for
# far less GC bookkeeping and compiler memory. MALLOC_ARENA_MAX=2 limits glibc per-thread arenas.
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xms24m -Xmx192m -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 -Xss512k -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"
ENV MALLOC_ARENA_MAX=2
ENTRYPOINT ["java", "-jar", "app.jar"]
