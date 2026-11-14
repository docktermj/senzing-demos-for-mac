package load;

import java.nio.file.Path;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

/**
 * Data processing: load one Senzing-ready source file into the Senzing repository.
 *
 * Environment setup follows sdk_guide(topic='load', language='java') → LoadViaFutures.java: read
 * SENZING_ENGINE_CONFIGURATION_JSON from the environment (never hard-coded), build ONE SzCoreEnvironment
 * for the process, get the engine, and destroy the environment at the end. The loading itself is the
 * shared SenzingLoader (see its header for the tier, the SQLite single-writer decision and what to raise
 * on PostgreSQL).
 *
 * Usage (from the project root, after src/scripts/sz-java.sh compile src/server/Json.java src/load/*.java):
 *   src/scripts/sz-java.sh run load.LoadSource <SOURCE> <input.jsonl> [--limit N] [--threads N] [--shuffle] [--resume]
 *
 * Example: src/scripts/sz-java.sh run load.LoadSource CREDIT_BUREAU data/senzing-ready/credit_bureau.jsonl --shuffle
 *
 * Prints ATTEMPTED / LOADED / FAILED / RETRIES / SECONDS / RECORDS_PER_SECOND. Failed records are in
 * logs/loading_errors.json. Exit code 1 when any record failed.
 * The top-level class is package-private so it may live in this snake_case file.
 */
class LoadSource {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: LoadSource <SOURCE> <input.jsonl> [--limit N] [--threads N] [--shuffle] [--resume]");
            System.exit(2);
        }
        String source = args[0];
        Path input = Path.of(args[1]);
        SenzingLoader.Options opt = new SenzingLoader.Options();
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--limit": opt.limit = Integer.parseInt(args[++i]); break;
                case "--threads": opt.threads = Integer.parseInt(args[++i]); break;
                case "--shuffle": opt.shuffle = true; break;
                case "--resume": opt.resume = true; break;
                default: throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }

        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // initialize the Senzing environment once for this process
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(LoadSource.class.getSimpleName())
            .verboseLogging(false)
            .build();

        SenzingLoader.Stats s;
        try {
            SzEngine engine = env.getEngine();
            s = SenzingLoader.load(engine, source, input, opt);
        } finally {
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
        }

        System.out.println("SOURCE=" + s.source);
        System.out.println("ATTEMPTED=" + s.attempted);
        System.out.println("LOADED=" + s.success);
        System.out.println("FAILED=" + s.errors);
        System.out.println("RETRIES=" + s.retries);
        if (s.skippedResume > 0) System.out.println("SKIPPED_ALREADY_LOADED=" + s.skippedResume);
        System.out.printf("SECONDS=%.2f%n", s.seconds());
        System.out.printf("RECORDS_PER_SECOND=%.1f%n", s.perSecond());
        if (!s.errorCodes.isEmpty()) System.out.println("ERROR_SUMMARY=" + s.errorCodes);
        System.exit(s.errors > 0 ? 1 : 0);
    }
}
