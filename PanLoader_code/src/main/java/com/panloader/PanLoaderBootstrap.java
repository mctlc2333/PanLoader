package com.panloader;

import com.panloader.api.ModMetadata;
import com.panloader.api.PanMod;
import com.panloader.core.*;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class PanLoaderBootstrap {

    private static final String VERSION = "0.2.0";
    private static final String DEFAULT_GAME_DIR = System.getProperty("user.dir") + "/.minecraft";
    private static final String DEFAULT_MC_VERSION = "1.20.1";

    private static ModManager modManager;
    private static Instrumentation instrumentation;
    private static EnvironmentFactory envFactory;

    public static void premain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
        com.panloader.mixin.MixinManager.setInstrumentation(inst);
        System.out.println("[PanLoader] Agent loaded (premain)");
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  PanLoader v" + VERSION);
        System.out.println("  A unified Forge + Fabric mod loader");
        System.out.println("  Sandbox Container Architecture");
        System.out.println("========================================");

        String gameDir = getArg(args, "gameDir", System.getProperty("panloader.gameDir", DEFAULT_GAME_DIR));
        String mcVersion = getArg(args, "version", System.getProperty("panloader.version", DEFAULT_MC_VERSION));

        System.out.println("[PanLoader] Game directory: " + gameDir);
        System.out.println("[PanLoader] MC version: " + mcVersion);

        try {
            Path gameDirPath = Paths.get(gameDir);
            Path modsPath = gameDirPath.resolve("mods");

            modManager = new ModManager();
            envFactory = new EnvironmentFactory(gameDirPath);

            CrossContainerBus bus = CrossContainerBus.getInstance();

            ModsFolderScanner scanner = new ModsFolderScanner(modsPath);
            List<ModsFolderScanner.ModCandidate> candidates = scanner.scan();

            System.out.println("[PanLoader] Found " + candidates.size() + " mod candidates in mods/");

            for (ModsFolderScanner.ModCandidate candidate : candidates) {
                try {
                    ModsFolderScanner.ModType modType = candidate.getType();
                    String jarName = candidate.getJarPath().getFileName().toString();

                    switch (modType) {
                        case PANLOADER:
                            System.out.println("  [PANLOADER] Routing to PanLoader container: " + jarName);
                            envFactory.routeMod(candidate);
                            break;
                        case FABRIC:
                            System.out.println("  [FABRIC] Routing to Fabric container: " + jarName);
                            envFactory.routeMod(candidate);
                            break;
                        case FORGE:
                            System.out.println("  [FORGE] Routing to Forge container: " + jarName);
                            envFactory.routeMod(candidate);
                            break;
                        case UNKNOWN:
                            System.out.println("  [UNKNOWN] Routing to PanLoader container (fallback): " + jarName);
                            envFactory.routeMod(candidate);
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("[PanLoader] Failed to route " + candidate.getJarPath().getFileName()
                            + ": " + e.getMessage());
                }
            }

            System.out.println("[PanLoader] Container routing complete:");
            for (ModContainer container : envFactory.getAllContainers()) {
                System.out.println("  [" + container.getContainerType().getDisplayName() + "] "
                        + container.getContainerId() + ": "
                        + container.getModJarPaths().size() + " mod(s)");
            }

            System.out.println("[PanLoader] Initializing containers...");
            envFactory.initializeAll();

            System.out.println("[PanLoader] Loaded " + envFactory.getTotalLoadedModCount()
                    + " mods across " + envFactory.getContainerCount() + " container(s).");

            for (ModContainer container : envFactory.getAllContainers()) {
                for (String modId : container.getLoadedModIds()) {
                    ModMetadata meta = container.getModMetadata(modId);
                    if (meta != null) {
                        System.out.println("  [+] " + meta.getName() + " v" + meta.getVersion()
                                + " (" + container.getContainerType().getDisplayName() + ")");
                    }
                }
            }

            LaunchHandler launcher = new LaunchHandler(mcVersion, gameDir, envFactory);
            launcher.launch(args);

        } catch (Exception e) {
            System.err.println("[PanLoader] Fatal error during startup:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String getArg(String[] args, String key, String defaultValue) {
        for (String arg : args) {
            if (arg.startsWith("--" + key + "=")) {
                return arg.substring(key.length() + 3);
            }
            if (arg.equals("--" + key)) {
                int idx = java.util.Arrays.asList(args).indexOf(arg);
                if (idx + 1 < args.length) {
                    return args[idx + 1];
                }
            }
        }
        return defaultValue;
    }

    public static ModManager getModManager() {
        return modManager;
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }

    public static EnvironmentFactory getEnvFactory() {
        return envFactory;
    }
}
