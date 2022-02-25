/**
 * @test /nodynamiccopyright/
 * @bug 8282160
 * @summary Prevent method does not override a super type method.
 * @compile/fail/ref=DoesNotOverrideWithErrorSuper.out -XDrawDiagnostics DoesNotOverrideWithErrorSuper.java
 */
public class DoesNotOverrideWithErrorSuper {
    public static class UnknownExtends extends Unknown {
        @Override public String toString() { return null; }
        @Override public void test() { }
    }
    public static class UnknownImplements implements Unknown {
        @Override public String toString() { return null; }
        @Override public void test() { }
    }
    public static interface UnknownImplementsIntf extends Unknown {
        @Override public default void test() { }
    }
    public static class UnknownExtendsBase extends Unknown {}
    public static class UnknownExtends2 extends UnknownExtendsBase {
        @Override public String toString() { return null; }
        @Override public void test() { }
    }
    public static interface UnknownImplementsBase extends Unknown {}
    public static class UnknownImplements2 implements UnknownImplementsBase {
        @Override public String toString() { return null; }
        @Override public void test() { }
    }
}
