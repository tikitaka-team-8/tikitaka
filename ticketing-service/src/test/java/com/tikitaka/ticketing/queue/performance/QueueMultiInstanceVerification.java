package com.tikitaka.ticketing.queue.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.ticketing.queue.application.*;
import com.tikitaka.ticketing.queue.config.QueueProperties;
import com.tikitaka.ticketing.queue.domain.*;
import com.tikitaka.ticketing.queue.infrastructure.RedisQueueRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Two processes with real Scheduler/Service/Repository, not two HTTP application deployments. */
public final class QueueMultiInstanceVerification {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int USERS = 500;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "worker".equals(args[0])) { worker(args); return; }
        boolean quota = args.length == 1 && "quota".equals(args[0]);
        if (args.length > 0 && !quota) throw new IllegalArgumentException("Expected no args or quota");
        Path out = Path.of("artifacts", "queue-multi", System.currentTimeMillis() + "-" + UUID.randomUUID()).toAbsolutePath();
        Files.createDirectories(out);
        System.out.println("Results: " + out);
        List<Process> children = new ArrayList<>();
        try (GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2.14-alpine"))
                .withExposedPorts(6379).withCommand("redis-server", "--appendonly", "yes", "--appendfsync", "everysec")) {
            redis.start();
            var factory = connect(redis.getHost(), redis.getMappedPort(6379));
            try {
                var template = new StringRedisTemplate(factory);
                var repository = new RedisQueueRepository(template);
                UUID session = UUID.randomUUID();
                var props = properties();
                write(out.resolve("environment.json"), Map.of("scope", "two standalone Scheduler JVMs; no HTTP routing",
                        "session", session.toString(), "users", USERS, "queueProperties", props.toString(),
                        "java", System.getProperty("java.version"), "redisImageId", redis.getContainerInfo().getImageId(),
                        "firstCycle", quota ? "stagger first reads to exercise distinct users in one server-second"
                                : "both workers read same first batch before either attempts admission"));
                for (String source : List.of("application/QueueAdmissionScheduler.java", "application/QueueAdmissionService.java",
                        "infrastructure/RedisQueueRepository.java")) {
                    Path from = Path.of("ticketing-service/src/main/java/com/tikitaka/ticketing/queue", source);
                    Files.copy(from, out.resolve(from.getFileName()));
                }
                Instant now = Instant.now();
                for (long user = 1; user <= USERS; user++) {
                    if (repository.createWaitingEntryIfAbsent(session, user, now, now.plusSeconds(3600), Duration.ofHours(1)).isEmpty())
                        throw new IllegalStateException("Seed failed");
                }
                repository.registerWaitingSession(session);
                for (int id = 1; id <= 2; id++) {
                    // @argfile avoids Windows command-line length limits for the test runtime classpath.
                    Path argfile = out.resolve("worker-" + id + ".args");
                    Files.writeString(argfile, "-cp\n" + quote(System.getProperty("java.class.path")) + "\n"
                            + QueueMultiInstanceVerification.class.getName() + "\nworker\n" + redis.getHost() + "\n"
                            + redis.getMappedPort(6379) + "\n" + session + "\n" + id + "\n" + quote(out.toString()) + "\n" + quota + "\n");
                    children.add(new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                            "@" + argfile).redirectErrorStream(true).redirectOutput(out.resolve("worker-" + id + ".log").toFile()).start());
                }
                try {
                    write(out.resolve("workers.json"), children.stream().map(Process::pid).toList());
                    long deadline = System.nanoTime() + Duration.ofSeconds(100).toNanos();
                    for (Process child : children) {
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0 || !child.waitFor(remaining, TimeUnit.NANOSECONDS) || child.exitValue() != 0)
                            throw new IllegalStateException("Worker failed/timed out; inspect worker logs");
                    }
                    List<Map<String, Object>> attempts = new ArrayList<>();
                    for (int id = 1; id <= 2; id++) {
                        for (String line : Files.readAllLines(out.resolve("attempts-" + id + ".jsonl"))) {
                            attempts.add(JSON.readValue(line, new com.fasterxml.jackson.core.type.TypeReference<>() {}));
                        }
                    }
                    Map<Long, Long> wins = new TreeMap<>();
                    Map<Long, String> winningHashes = new HashMap<>();
                    Map<Long, Long> buckets = new TreeMap<>();
                    Map<Long, Long> attemptsByUser = new TreeMap<>();
                    for (var row : attempts) {
                        long user = ((Number) row.get("user")).longValue();
                        attemptsByUser.merge(user, 1L, Long::sum);
                        if (Boolean.TRUE.equals(row.get("success"))) {
                            wins.merge(user, 1L, Long::sum);
                            winningHashes.put(user, (String) row.get("tokenHash"));
                            buckets.merge(((Number) row.get("responseAtMs")).longValue() / 1000, 1L, Long::sum);
                        }
                    }
                    boolean valid = wins.size() == USERS && wins.values().stream().allMatch(n -> n == 1)
                            && (quota || attemptsByUser.values().stream().filter(n -> n > 1).count() >= 50);
                    Set<String> expectedKeys = new HashSet<>();
                    List<Map<String, Object>> entries = new ArrayList<>();
                    for (long user = 1; user <= USERS; user++) {
                        var entry = repository.findEntry(session, user).orElseThrow();
                        String reference = repository.findAdmissionTokenReference(session, user).orElseThrow();
                        var token = repository.findAdmissionToken(session, reference).orElseThrow();
                        valid &= entry.status() == QueueStatus.ADMITTED && entry.sequence() == user
                                && hash(reference).equals(winningHashes.get(user))
                                && token.userId() == user && token.sessionId().equals(session)
                                && token.status() == AdmissionTokenStatus.ACTIVE && token.expiresAt().isAfter(Instant.now());
                        expectedKeys.add("queue:admission-token:{" + session + "}:" + reference);
                        entries.add(Map.of("user", user, "sequence", entry.sequence(), "state", entry.status().name(), "tokenHash", hash(reference)));
                    }
                    // Bounded dedicated Redis (500 users only); compare all token keys to detect orphan tokens.
                    Set<String> actualKeys = template.keys("queue:admission-token:{" + session + "}:*");
                    valid &= expectedKeys.size() == USERS && expectedKeys.equals(actualKeys) && repository.countWaitingUsers(session) == 0;
                    write(out.resolve("entries.json"), entries);
                    String quotaVerdict = "UNDECIDED: response-time buckets are observational, not server commit time or rolling-window proof";
                    if (quota) {
                        var evidence = quotaEvidence(attempts);
                        write(out.resolve("quota.json"), evidence);
                        quotaVerdict = (String) evidence.get("verdict");
                    }
                    write(out.resolve("summary.json"), Map.of("integrityPassed", valid, "users", USERS,
                            "successfulAdmissions", wins.values().stream().mapToLong(Long::longValue).sum(),
                            "attempts", attempts.size(), "usersWithCompetingAttempts", attemptsByUser.values().stream().filter(n -> n > 1).count(),
                            "admissionsByResponseEpochSecond", buckets,
                            "quotaVerdict", quotaVerdict));
                    if (!valid) throw new IllegalStateException("Integrity mismatch; see summary/entries/attempts");
                    if (quota && !"PASS".equals(quotaVerdict))
                        throw new IllegalStateException("Aggregate quota " + quotaVerdict + "; inspect quota.json (integrity passed)");
                    System.out.println("COMPLETE: integrity passed; quota=" + quotaVerdict);
                } finally {
                    for (Process child : children) {
                        if (child.isAlive()) { child.destroyForcibly(); child.waitFor(10, TimeUnit.SECONDS); }
                    }
                }
            } finally { factory.destroy(); }
        } catch (Exception failure) {
            for (Process child : children) if (child.isAlive()) child.destroyForcibly();
            write(out.resolve("failure.json"), Map.of("error", failure.toString()));
            throw failure;
        }
    }

    private static void worker(String[] args) throws Exception {
        UUID session = UUID.fromString(args[3]);
        String id = args[4];
        Path out = Path.of(args[5]);
        boolean quota = args.length > 6 && Boolean.parseBoolean(args[6]);
        var factory = connect(args[1], Integer.parseInt(args[2]));
        var meters = new SimpleMeterRegistry();
        try (var attempts = Files.newBufferedWriter(out.resolve("attempts-" + id + ".jsonl"));
             var cycles = Files.newBufferedWriter(out.resolve("cycles-" + id + ".jsonl"))) {
            var template = new StringRedisTemplate(factory);
            var repo = new RedisQueueRepository(template) {
                boolean first = true;
                @Override public List<QueueEntry> findWaitingEntries(UUID sid, int limit) {
                    var result = super.findWaitingEntries(sid, limit);
                    if (first && !quota) {
                        first = false;
                        template.opsForSet().add("verification:first-read", id);
                        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                        while (template.opsForSet().size("verification:first-read") < 2) {
                            if (System.nanoTime() > deadline) throw new IllegalStateException("First-read barrier timed out");
                            pause(10);
                        }
                    }
                    return result;
                }
                @Override public boolean admitIfWaiting(QueueEntry entry, AdmissionToken token, Duration ttl, Duration tokenTtl) {
                    long redisBefore = quota ? redisTime(template) : 0;
                    boolean success = super.admitIfWaiting(entry, token, ttl, tokenTtl);
                    long redisAfter = quota ? redisTime(template) : 0;
                    try {
                        attempts.write(JSON.writeValueAsString(Map.of("worker", id, "user", entry.userId(),
                                "tokenHash", hash(token.token()), "success", success, "responseAtMs", System.currentTimeMillis(),
                                "redisBeforeMs", redisBefore, "redisAfterMs", redisAfter)) + "\n");
                        attempts.flush();
                    } catch (Exception error) { throw new IllegalStateException("Cannot preserve admission evidence", error); }
                    return success;
                }
            };
            var metrics = new QueueMetrics(meters);
            var scheduler = new QueueAdmissionScheduler(new QueueAdmissionService(repo, properties(), Clock.systemUTC(), metrics), metrics);
            if (quota) {
                template.opsForSet().add("verification:ready", id);
                long readyDeadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                while (template.opsForSet().size("verification:ready") < 2) {
                    if (System.nanoTime() > readyDeadline) throw new IllegalStateException("Ready barrier timed out");
                    pause(10);
                }
                if ("1".equals(id)) {
                    // Align the first probe; subsequent cycles retain the existing 1s fixed delay.
                    long target = (redisTime(template) / 1000 + 1) * 1000 + 50;
                    while (redisTime(template) < target) pause(5);
                } else {
                    while (!template.hasKey("verification:first-cycle-done")) {
                        if (System.nanoTime() > readyDeadline) throw new IllegalStateException("First-cycle barrier timed out");
                        pause(5);
                    }
                }
            }
            long deadline = System.nanoTime() + Duration.ofSeconds(85).toNanos();
            int cycle = 0;
            while (repo.countWaitingUsers(session) > 0) {
                if (System.nanoTime() > deadline) throw new IllegalStateException("Drain timed out");
                long start = System.currentTimeMillis();
                double before = meters.get("queue.admission").counter().count();
                scheduler.processQueueAdmissions();
                long admitted = (long) (meters.get("queue.admission").counter().count() - before);
                cycles.write(JSON.writeValueAsString(Map.of("worker", id, "cycle", ++cycle, "startMs", start,
                        "endMs", System.currentTimeMillis(), "admitted", admitted)) + "\n");
                cycles.flush();
                if (quota && cycle == 1 && "1".equals(id)) template.opsForValue().set("verification:first-cycle-done", "1");
                if (admitted > 50) throw new IllegalStateException("Per-cycle batch exceeded");
                pause(1000); // actual fixedDelay after each complete cycle; no accelerated logical clock
            }
        } finally { meters.close(); factory.destroy(); }
    }

    private static QueueProperties properties() {
        var loader = new YamlPropertiesFactoryBean();
        loader.setResources(new ClassPathResource("application.yaml"));
        Properties p = Objects.requireNonNull(loader.getObject());
        if (!"50".equals(p.getProperty("queue.admission-batch-size")) || !Duration.ofSeconds(1).equals(Duration.parse(p.getProperty("queue.admission-interval"))))
            throw new IllegalStateException("Expected current batch 50 / interval 1s");
        return new QueueProperties(Duration.parse(p.getProperty("queue.admission-token-ttl")), Duration.parse(p.getProperty("queue.retention-after-sales-close")),
                50, Integer.parseInt(p.getProperty("queue.expiration-batch-size")), Duration.parse(p.getProperty("queue.waiting-heartbeat-timeout")));
    }
    private static LettuceConnectionFactory connect(String host, int port) {
        var factory = new LettuceConnectionFactory(host, port); factory.afterPropertiesSet(); factory.start(); return factory;
    }
    private static long redisTime(StringRedisTemplate template) {
        return Objects.requireNonNull(template.execute((org.springframework.data.redis.core.RedisCallback<Long>)
                connection -> connection.serverCommands().time(TimeUnit.MILLISECONDS)));
    }

    // Actual Lua commit lies between these two Redis TIME observations. Boundary-crossing
    // attempts contribute to every possible bucket, never falsely assigned to a response bucket.
    static Map<String, Object> quotaEvidence(List<Map<String, Object>> attempts) {
        Map<Long, Long> definite = new TreeMap<>();
        Map<Long, Long> possible = new TreeMap<>();
        int ambiguous = 0;
        boolean invalidTime = false;
        for (var row : attempts) {
            if (!Boolean.TRUE.equals(row.get("success"))) continue;
            long before = ((Number) row.get("redisBeforeMs")).longValue();
            long after = ((Number) row.get("redisAfterMs")).longValue();
            if (before <= 0 || after < before || after - before > 100_000) { invalidTime = true; continue; }
            long first = before / 1000, last = after / 1000;
            if (first == last) definite.merge(first, 1L, Long::sum);
            else ambiguous++;
            for (long second = first; second <= last; second++) possible.merge(second, 1L, Long::sum);
        }
        String verdict = invalidTime || possible.isEmpty() ? "INCONCLUSIVE"
                : definite.values().stream().anyMatch(n -> n > 50) ? "FAIL"
                : possible.values().stream().anyMatch(n -> n > 50) ? "INCONCLUSIVE" : "PASS";
        return Map.of("policy", "per session, all workers combined, max 50 successes per Redis epoch second",
                "verdict", verdict, "definiteAdmissionsBySecond", definite, "possibleAdmissionsBySecond", possible,
                "boundaryCrossingSuccesses", ambiguous, "invalidTimeBounds", invalidTime,
                "scope", "observed run only; two extra TIME commands per admission attempt; assumes stable Redis clock");
    }
    private static void pause(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }
    private static String hash(String text) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private static String quote(String text) { return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    private static void write(Path path, Object value) throws Exception { JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value); }
}
