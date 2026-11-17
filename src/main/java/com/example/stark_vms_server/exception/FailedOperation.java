package com.example.stark_vms_server.exception;

import java.util.Locale;

public final class FailedOperation {
    private static final String VEHICLES = "/api/vehicles";
    private static final String LOGIN = "/api/auth/login";
    private static final String ME = "/api/auth/me";
    private static final String HEALTH = "/api/health";

    private FailedOperation() {
    }

    public static String message(String method, String path, String reason) {
        String what = of(method, path);
        String why = reason == null ? "" : reason.strip();

        if (why.isEmpty()) {
            return what;
        }
        return what + " " + why;
    }

    public static String of(String method, String path) {
        String verb = method == null ? "" : method.toUpperCase(Locale.ROOT);
        String target = path == null ? "" : path;

        if (target.startsWith(VEHICLES)) {
            boolean collection = target.equals(VEHICLES) || target.startsWith(VEHICLES + "?");
            return switch (verb) {
                case "POST" -> "Vehicle creation failed.";
                case "PUT", "PATCH" -> "Vehicle update failed.";
                case "DELETE" -> "Vehicle deletion failed.";
                case "GET" -> collection
                        ? "Vehicles could not be loaded."
                        : "Vehicle could not be loaded.";
                default -> "Vehicle request failed.";
            };
        }
        if (target.startsWith(LOGIN)) {
            return "Sign-in failed.";
        }
        if (target.startsWith(ME)) {
            return "Account could not be loaded.";
        }
        if (target.startsWith(HEALTH)) {
            return "Health check failed.";
        }
        return "Request failed.";
    }
}
