/**
 * @test /nodynamiccopyright/
 * @bug 8259359
 * @summary Check that unexpected super constructor invocation qualifier is attributed
 * @compile/fail/ref=SuperConstructorCallBroken.out -XDshould-stop.at=FLOW -XDdev -XDrawDiagnostics SuperConstructorCallBroken.java
 */
public class SuperConstructorCallBroken extends Undefined1 {
     public SuperConstructorCallBroken(int i) {
         new Undefined2() { public void test(int i) { Undefined3 u; } }.super();
     }
     interface I<T> {}
}
