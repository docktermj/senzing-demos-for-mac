// load_customer_data.java -- Data processing, Step 3: the production loading program (command line).
//
// Loads one or more Senzing JSON Lines files with the shared loading engine in RecordLoader.java,
// which carries the full provenance (MCP LoadViaFutures scaffold), the production tier and the
// concurrency decision. In short: medium production tier, threaded pattern, ONE worker on SQLite
// (no concurrent writes); raise --threads toward 2-8 per CPU core on PostgreSQL.
// The environment setup below (SzCoreEnvironment builder, getEngine, destroy) is the scaffold's.
//
// Usage: src/scripts/run-java.sh LoadCustomerData <file.jsonl> [<file.jsonl> ...] [--threads N] [--limit N]
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

class LoadCustomerData {
    public static void main(String[] args) throws Exception {
        List<Path> files = new ArrayList<>();
        int threads = RecordLoader.DEFAULT_THREAD_COUNT;
        long limit = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--threads" -> threads = Integer.parseInt(args[++i]);
                case "--limit" -> limit = Long.parseLong(args[++i]);
                default -> files.add(Path.of(args[i]));
            }
        }
        if (files.isEmpty()) {
            System.err.println("usage: LoadCustomerData <file.jsonl> [...] [--threads N] [--limit N]");
            System.exit(2);
        }
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        // initialize the Senzing environment ONCE for the whole process
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(LoadCustomerData.class.getSimpleName())
            .verboseLogging(false)
            .build();
        try {
            RecordLoader loader = new RecordLoader(env.getEngine(), threads);
            for (Path file : files) {
                System.out.println("Loading " + file + " (threads=" + threads + (limit > 0 ? ", limit=" + limit : "") + ")");
                RecordLoader.Stats stats = loader.load(file, limit);
                System.out.println("DONE " + stats);
            }
        } finally {
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
        }
    }
}
