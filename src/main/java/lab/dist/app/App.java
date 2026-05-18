package lab.dist.app;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import lab.dist.simulation.SimulationRunner;

public final class App {
    private App() {
    }

    public static void main(String[] args) throws IOException {
        SimulationRunner runner = new SimulationRunner();
        if (args.length == 0 || "demo".equalsIgnoreCase(args[0])) {
            runner.runDemo(Path.of("runs"));
            return;
        }

        String command = args[0];
        String[] tail = Arrays.copyOfRange(args, 1, args.length);
        switch (command.toLowerCase()) {
            case "simulate" -> runner.run(SimulationRunner.fromArgs(tail));
            case "recover" -> runner.recover(readWorkspace(tail));
            default -> printUsage();
        }
    }

    private static Path readWorkspace(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--workspace".equals(args[i])) {
                return Path.of(args[i + 1]);
            }
        }
        return Path.of("runs", "default");
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  demo");
        System.out.println("  simulate --workspace <path> --users <n> --requests <n> --crashAt <n> --failure <NONE|AFTER_WAL_APPEND|BEFORE_DATA_APPLY|AFTER_DATA_APPLY> --recover <true|false> --seed <n>");
        System.out.println("  recover --workspace <path>");
    }
}
