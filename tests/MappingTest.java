package dev.antonio.tvremap;

public final class MappingTest {
    public static void main(String[] args) {
        check(Mapping.identity(200, 752).equals(Mapping.identity(200, 0)), "Key identity must survive scan-code changes");
        check(!Mapping.identity(0, 752).equals(Mapping.identity(0, 753)), "Unknown vendor keys must remain distinct");
        check(!Mapping.identity(752, 0).equals(Mapping.identity(0, 752)), "Scan and key namespaces must not collide");
        System.out.println("3 mapping identity checks passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
