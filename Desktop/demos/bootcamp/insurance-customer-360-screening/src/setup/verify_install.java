package setup;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

/**
 * SDK setup, Steps 4 and 5a: verify the Java binding loads, report the engine
 * version, report WHERE the binding resolved from, and read the active license.
 *
 * Derived from senzing/code-snippets-v4 (fetched via the Senzing MCP server's
 * generate_scaffold(language='java', workflow='information'), sha256 verified):
 *   java/snippets/information/GetVersion.java
 *   java/snippets/information/GetLicense.java
 * Deviations from the scaffold, all outside the Senzing calls:
 *   - the two snippets are combined into one program with one environment;
 *   - it prints the location of the sz-sdk.jar the SzProduct class was loaded from;
 *   - it prints labelled lines so a helper can save version and license JSON.
 * The SDK calls (SzCoreEnvironment builder, getProduct, getVersion, getLicense,
 * destroy) are unchanged from the scaffold.
 *
 * This creates NO engine: an engine needs the engine configuration and seeded
 * datastore that later steps create.
 *
 * The top-level class is package-private so it may live in this snake_case file.
 */
class VerifyInstall {
    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // where did the binding come from?
        java.security.CodeSource src = SzProduct.class.getProtectionDomain().getCodeSource();
        System.out.println("BINDING_PATH=" + (src == null ? "unknown" : src.getLocation()));

        // create a descriptive instance name (can be anything)
        String instanceName = VerifyInstall.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();

        try {
            SzProduct product = env.getProduct();

            System.out.println("VERSION_JSON=" + product.getVersion());
            System.out.println("LICENSE_JSON=" + product.getLicense());

        } catch (SzException e) {
            // handle any exception that may have occurred
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            e.printStackTrace();
            throw new RuntimeException(e);

        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof RuntimeException) {
                throw ((RuntimeException) e);
            }
            throw new RuntimeException(e);

        } finally {
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
        }
    }
}
