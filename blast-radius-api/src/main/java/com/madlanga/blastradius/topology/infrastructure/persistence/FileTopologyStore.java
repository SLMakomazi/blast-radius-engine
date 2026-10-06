package com.madlanga.blastradius.topology.infrastructure.persistence;

import com.madlanga.blastradius.topology.domain.RetainedTopology;
import com.madlanga.blastradius.topology.application.port.TopologyStore;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import tools.jackson.databind.json.JsonMapper;

/** Single-writer local store. Atomic replacement prevents partially written snapshots. */
public final class FileTopologyStore implements TopologyStore {
    private final Path directory;
    private final JsonMapper mapper = JsonMapper.builder().build();
    public FileTopologyStore(Path directory) { this.directory = directory; }

    @Override public synchronized RetainedTopology load(String applicationId, String environment) {
        Path path = path(applicationId, environment);
        if (!Files.exists(path)) return RetainedTopology.empty(applicationId, environment);
        try {
            RetainedTopology result = mapper.readValue(Files.readString(path), RetainedTopology.class);
            if (!applicationId.equals(result.applicationId()) || !environment.equals(result.environment()))
                throw new IllegalStateException("Topology scope mismatch");
            return result;
        } catch (IOException | tools.jackson.core.JacksonException e) {
            throw new IllegalStateException("Cannot read retained topology; refusing to assume an empty topology", e);
        }
    }
    @Override public synchronized void save(RetainedTopology topology) {
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, ".topology-", ".tmp");
            Files.writeString(temporary, mapper.writeValueAsString(topology));
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temporary, path(topology.applicationId(), topology.environment()),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | tools.jackson.core.JacksonException e) {
            throw new IllegalStateException("Cannot persist retained topology", e);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* orphan is never read */ }
        }
    }
    private Path path(String applicationId, String environment) {
        try {
            String scope = applicationId.length() + ":" + applicationId + environment.length() + ":" + environment;
            return directory.resolve(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(scope.getBytes(StandardCharsets.UTF_8))) + ".json");
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
