package com.tikitaka.ticketing.queue.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.management.OperatingSystemMXBean;
import com.tikitaka.ticketing.queue.application.QueueAdmissionScheduler;
import com.tikitaka.ticketing.queue.application.QueueAdmissionService;
import com.tikitaka.ticketing.queue.application.QueueMetrics;
import com.tikitaka.ticketing.queue.config.QueueProperties;
import com.tikitaka.ticketing.queue.domain.QueueEntry;
import com.tikitaka.ticketing.queue.domain.QueueStatus;
import com.tikitaka.ticketing.queue.infrastructure.RedisQueueRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.BufferedWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Explicit JavaExec entry point, not an automatically discovered test. */
public final class QueueSchedulerBaseline {
    private static final int USERS = 5000;
    private static final String REDIS_IMAGE = "redis:7.2.14-alpine";
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Path out = Path.of("artifacts", "queue-scheduler", LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + UUID.randomUUID().toString().substring(0, 8));
        Files.createDirectories(out);
        System.out.println("Results: " + out.toAbsolutePath());
        Properties config = configuration();
        QueueProperties properties = new QueueProperties(
                Duration.parse(config.getProperty("queue.admission-token-ttl")),
                Duration.parse(config.getProperty("queue.retention-after-sales-close")),
                Integer.parseInt(config.getProperty("queue.admission-batch-size")),
                Integer.parseInt(config.getProperty("queue.expiration-batch-size")),
                Duration.parse(config.getProperty("queue.waiting-heartbeat-timeout")));
        if (properties.admissionBatchSize() != 50) throw new IllegalStateException("Baseline requires batch=50");

        // Dedicated Redis only: the running Ticketing scheduler cannot consume these fixtures.
        try (GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                .withExposedPorts(6379)
                .withCommand("redis-server", "--appendonly", "yes", "--appendfsync", "everysec")) {
            redis.start();
            LettuceConnectionFactory connection = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            connection.afterPropertiesSet();
            connection.start();
            SimpleMeterRegistry meters = new SimpleMeterRegistry();
            try {
                StringRedisTemplate template = new StringRedisTemplate(connection);
                TimedRepository repository = new TimedRepository(template);
                Instant logicalNow = Instant.now();
                Clock clock = Clock.fixed(logicalNow, ZoneOffset.UTC);
                QueueMetrics metrics = new QueueMetrics(meters);
                QueueAdmissionScheduler scheduler = new QueueAdmissionScheduler(
                        new QueueAdmissionService(repository, properties, clock, metrics), metrics);
                UUID session = UUID.randomUUID();
                Map<String, Object> environment = new LinkedHashMap<>();
                environment.put("javaVersion", System.getProperty("java.version"));
                environment.put("os", System.getProperty("os.name"));
                environment.put("availableProcessors", Runtime.getRuntime().availableProcessors());
                environment.put("jvmMaxHeapBytes", Runtime.getRuntime().maxMemory());
                environment.put("redisImage", REDIS_IMAGE);
                environment.put("redisImageId", redis.getContainerInfo().getImageId());
                environment.put("redisPersistence", "appendonly yes; appendfsync everysec; disposable container");
                environment.put("branch", git("branch", "--show-current"));
                environment.put("commit", git("rev-parse", "HEAD"));
                environment.put("workingTreeStatus", git("status", "--short"));
                Map<String, String> hashes = new LinkedHashMap<>();
                for (String source : List.of(
                        "src/main/java/com/tikitaka/ticketing/queue/application/QueueAdmissionScheduler.java",
                        "src/main/java/com/tikitaka/ticketing/queue/application/QueueAdmissionService.java",
                        "src/main/java/com/tikitaka/ticketing/queue/infrastructure/RedisQueueRepository.java",
                        "src/main/resources/application.yaml",
                        "src/test/java/com/tikitaka/ticketing/queue/performance/QueueSchedulerBaseline.java")) {
                    hashes.put(source, java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                            .digest(Files.readAllBytes(Path.of("ticketing-service", source)))));
                }
                environment.put("sourceSha256", hashes);
                environment.put("sessionId", session.toString());
                environment.put("users", USERS);
                environment.put("queueProperties", properties.toString());
                environment.put("configuredInterval", config.getProperty("queue.admission-interval"));
                environment.put("mode", "back-to-back real scheduler calls; no fixedDelay sleep; no HTTP; single session");
                environment.put("clock", "fixed at " + logicalNow + "; expiry scans run but fixture aging is excluded");
                environment.put("cpuScope", "standalone Ticketing component runner JVM, NOT running Ticketing container");
                write(out.resolve("environment.json"), environment);

                long seedStart = System.nanoTime();
                for (long user = 1; user <= USERS; user++) {
                    if (repository.createWaitingEntryIfAbsent(session, user, logicalNow,
                            logicalNow.plus(Duration.ofHours(2)), Duration.ofHours(2)).isEmpty()) {
                        throw new IllegalStateException("Fixture creation failed: " + user);
                    }
                }
                repository.registerWaitingSession(session);
                if (repository.countWaitingUsers(session) != USERS) throw new IllegalStateException("Incomplete seed");
                write(out.resolve("seed.json"), Map.of("users", USERS, "durationMs", ms(System.nanoTime() - seedStart)));

                Map<String, Properties> before = redisInfo(connection);
                write(out.resolve("redis-before.json"), before);
                List<Cycle> cycles = new ArrayList<>();
                OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                long total = 0;
                long runStart = System.nanoTime();
                try (BufferedWriter csv = Files.newBufferedWriter(out.resolve("cycles.csv"))) {
                    csv.write("cycle,waitingStart,admitted,totalAdmitted,durationMs,findWaitingEntriesMs,jvmCpuCores,jvmHeapBytes\n");
                    while (total < USERS) {
                        if (cycles.size() >= USERS / 50 || System.nanoTime() - runStart > Duration.ofMinutes(10).toNanos()) {
                            throw new IllegalStateException("Incomplete baseline; inspect cycles.csv (not a team SLO failure)");
                        }
                        long waiting = repository.countWaitingUsers(session);
                        double admittedBefore = meters.get("queue.admission").counter().count();
                        repository.findNanos = 0;
                        long cpuStart = os.getProcessCpuTime();
                        long start = System.nanoTime();
                        scheduler.processQueueAdmissions();
                        long duration = System.nanoTime() - start;
                        long cpuEnd = os.getProcessCpuTime();
                        long admitted = (long) (meters.get("queue.admission").counter().count() - admittedBefore);
                        total += admitted;
                        double cpuCores = cpuStart < 0 || cpuEnd < 0 ? -1 : (double) (cpuEnd - cpuStart) / duration;
                        Cycle cycle = new Cycle(cycles.size() + 1, waiting, admitted, total, ms(duration),
                                ms(repository.findNanos), cpuCores, ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
                        cycles.add(cycle);
                        csv.write(cycle.csv());
                        csv.flush();
                        if (waiting != USERS - (total - admitted) || admitted != Math.min(50, waiting)) {
                            throw new IllegalStateException("Unexpected admission progress at cycle " + cycle.cycle());
                        }
                        if (cycle.cycle() % 10 == 0) System.out.println("cycle=" + cycle.cycle() + " totalAdmitted=" + total);
                    }
                }
                double elapsedMs = ms(System.nanoTime() - runStart);
                Map<String, Properties> after = redisInfo(connection);
                write(out.resolve("redis-after.json"), after);
                write(out.resolve("cycles.json"), cycles);
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("totalAdmitted", total);
                summary.put("cycles", cycles.size());
                double[] durations = cycles.stream().mapToDouble(Cycle::durationMs).sorted().toArray();
                double cycleTotalMs = cycles.stream().mapToDouble(Cycle::durationMs).sum();
                summary.put("cycleDurationMs", Map.of("avg", cycleTotalMs / cycles.size(), "p50", percentile(durations, .50),
                        "p95", percentile(durations, .95), "p99", percentile(durations, .99), "max", durations[durations.length - 1]));
                summary.put("findWaitingEntriesTotalMs", cycles.stream().mapToDouble(Cycle::findWaitingEntriesMs).sum());
                summary.put("cycleTimeTotalMs", cycleTotalMs);
                summary.put("admissionsPerSecondCycleTime", total * 1000.0 / cycleTotalMs);
                summary.put("admissionsPerSecondWallTime", total * 1000.0 / elapsedMs);
                summary.put("processing5000WallMs", elapsedMs);
                summary.put("redisTotalCommandsDelta", delta(before, after, "total_commands_processed"));
                summary.put("redisRejectedConnectionsDelta", delta(before, after, "rejected_connections"));
                summary.put("redisOpsBefore", before.get("stats").getProperty("instantaneous_ops_per_sec"));
                summary.put("redisOpsAfter", after.get("stats").getProperty("instantaneous_ops_per_sec"));
                // Verification is outside cycle/Redis measurement windows.
                boolean valid = repository.countWaitingUsers(session) == 0;
                for (long user = 1; user <= USERS; user++) {
                    QueueEntry entry = repository.findEntry(session, user).orElseThrow();
                    valid &= entry.status() == QueueStatus.ADMITTED && entry.sequence() == user;
                }
                summary.put("all5000AdmittedWithOriginalSequence", valid);
                summary.put("status", valid ? "COMPLETE" : "INVALID");
                write(out.resolve("summary.json"), summary);
                if (!valid) throw new IllegalStateException("Final Redis integrity failure");
                System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
            } finally {
                meters.close();
                connection.destroy();
            }
        } catch (Exception error) {
            write(out.resolve("failure.json"), Map.of("type", error.getClass().getName(), "message", String.valueOf(error.getMessage())));
            throw error;
        }
    }

    private static Properties configuration() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        return java.util.Objects.requireNonNull(yaml.getObject());
    }

    private static Map<String, Properties> redisInfo(LettuceConnectionFactory factory) {
        try (var connection = factory.getConnection()) {
            Map<String, Properties> info = new LinkedHashMap<>();
            for (String section : List.of("stats", "memory", "commandstats")) {
                info.put(section, connection.serverCommands().info(section));
            }
            return info;
        }
    }

    private static long delta(Map<String, Properties> before, Map<String, Properties> after, String key) {
        return Long.parseLong(after.get("stats").getProperty(key)) - Long.parseLong(before.get("stats").getProperty(key));
    }

    private static double percentile(double[] sorted, double fraction) {
        double index = (sorted.length - 1) * fraction;
        int low = (int) index;
        return sorted[low] + (sorted[Math.min(low + 1, sorted.length - 1)] - sorted[low]) * (index - low);
    }

    private static double ms(long nanos) { return nanos / 1_000_000.0; }
    private static void write(Path path, Object value) throws Exception {
        JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }
    private static String git(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) throw new IllegalStateException("Cannot record git environment");
        return output;
    }

    public record Cycle(int cycle, long waitingStart, long admitted, long totalAdmitted, double durationMs,
                        double findWaitingEntriesMs, double jvmCpuCores, long jvmHeapBytes) {
        String csv() {
            return String.format(java.util.Locale.ROOT, "%d,%d,%d,%d,%.6f,%.6f,%.6f,%d%n",
                    cycle, waitingStart, admitted, totalAdmitted, durationMs, findWaitingEntriesMs, jvmCpuCores, jvmHeapBytes);
        }
    }

    private static final class TimedRepository extends RedisQueueRepository {
        private long findNanos;
        private TimedRepository(StringRedisTemplate redis) { super(redis); }
        @Override
        public List<QueueEntry> findWaitingEntries(UUID sessionId, int limit) {
            long start = System.nanoTime();
            try { return super.findWaitingEntries(sessionId, limit); }
            finally { findNanos += System.nanoTime() - start; }
        }
    }
}
