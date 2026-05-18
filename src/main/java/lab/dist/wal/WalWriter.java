package lab.dist.wal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import lab.dist.domain.AccountCommand;

public final class WalWriter {
    private final Path walPath;

    public WalWriter(Path walPath) throws IOException {
        this.walPath = walPath;
        Path parent = walPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(walPath)) {
            Files.createFile(walPath);
        }
    }

    public Path walPath() {
        return walPath;
    }

    public void appendCommand(AccountCommand command) throws IOException {
        append(WalRecord.fromCommand(command));
    }

    public void appendCommit(String requestId) throws IOException {
        append(WalRecord.commit(requestId));
    }

    private void append(WalRecord record) throws IOException {
        byte[] bytes = (record.serialize() + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(
                walPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND)) {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        }
    }
}
