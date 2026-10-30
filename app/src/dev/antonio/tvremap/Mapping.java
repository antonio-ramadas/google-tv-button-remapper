package dev.antonio.tvremap;

/** Immutable persisted shortcut. Android key codes are stable across remote reconnections. */
final class Mapping {
    final int keyCode;
    final int scanCode;
    final String name;
    final String action;
    final String target;

    Mapping(int keyCode, int scanCode, String name, String action, String target) {
        this.keyCode = keyCode;
        this.scanCode = scanCode;
        this.name = name;
        this.action = action;
        this.target = target;
    }

    String id() { return identity(keyCode, scanCode); }

    static String identity(int code, int scan) {
        // Some vendor remotes report UNKNOWN; their scan code distinguishes buttons.
        return code == 0 ? "scan_" + scan : "key_" + code;
    }

}
