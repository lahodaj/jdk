/*
 * @test /nodynamiccopyright/
 * @bug 8304487
 * @summary Compiler Implementation for Primitive types in patterns, instanceof, and switch (Preview)
 * @compile/fail/ref=PrimitivePatternsSwitchErrors.out --enable-preview -source ${jdk.version} -XDrawDiagnostics -XDshould-stop.at=FLOW PrimitivePatternsSwitchErrors.java
 */
public class PrimitivePatternsSwitchErrors {
    record R_int(int x) {}

    public static void dominationBetweenPrimitivePatterns() {
        int i = 42;
        switch (i) {
            case short s -> System.out.println("its a short");
            case byte b  -> System.out.println("its a byte"); // Error - dominated!
            default      -> System.out.println("any other integral value");
        }
    }

    public static int dominationWithRecordPatterns() {
        R_int r = new R_int(42);
        return switch (r) {
            case R_int(int x) -> 1;
            case R_int(byte x) -> 2;  // Error - dominated!
        };
    }

    public static int inconvertibleNestedComponent() {
        R_int r = new R_int(42);
        return switch (r) {
            case R_int(Long x) -> 1; // inconvertible
        };
    }

    public static int nonExhaustive1() {
        int i = 42;
        return switch (i) {  // Error - not exhaustive
            case short s -> s;
        };
    }

    public static int nonExhaustive2() {
        int i = 42;
        return switch (i) { // Error - not exhaustive
            case byte  b -> 1;
            case short s -> 2;
        };
    }

    public static int nonExhaustive3() {
        int i = 42;
        return switch (i) { // Error - not exhaustive
            case byte  b -> 1;
            case float f -> 2;
        };
    }

    public static int dominationBetweenBoxedAndPrimitive() {
        int i = 42;
        return switch (i) {
            case Integer ib  -> ib;
            case byte ip     -> ip; // Error - dominated!
        };
    }

    public static int constantDominatedWithPrimitivePattern() {
        int i = 42;
        return switch (i) {
            case int j -> 42;
            case 43    -> -1;   // Error - dominated!
        };
    }

    public static int constantDominatedWithFloatPrimitivePattern() {
        float f = 42.0f;
        return switch (f) {
            case Float ff -> 42;
            case 43.0f    -> -1;   // Error - dominated!
        };
    }

    void switchLongOverByte(byte b) {
        switch (b) {
            case 0L: return ;
        }
    }

    void switchOverPrimitiveFloatFromInt(float f) {
        switch (f) {
            case 16777216:
                break;
            case 16777217:
                break;
            default:
                break;
        }
    }

    void switchOverNotRepresentableFloat(Float f) {
        switch (f) {
            case 1.0f:
                break;
            case 0.999999999f:
                break;
            case Float fi:
                break;
        }
    }

    int switchOverPrimitiveBooleanExhaustiveWithNonPermittedDefault(boolean b) {
        return switch (b) {
            case true -> 1;
            case false -> 2;
            default -> 3;
        };
    }
}
