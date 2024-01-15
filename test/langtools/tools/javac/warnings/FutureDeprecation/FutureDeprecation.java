/**
 * @test
 * @compile --release 8 FutureDeprecation.java
 */
public class FutureDeprecation {
    public void test() {
        SecurityManager sm;
        Thread t = null;
        t.stop(new Throwable());
        t.stop();
    }
}
