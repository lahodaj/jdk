/**
 * @test
 * @enablePreview
 * @compile/fail/ref=MultipleMatchersErrors.out -XDrawDiagnostics MultipleMatchersErrors.java
 */

public class MultipleMatchersErrors {
    private static String test1(Object o) {
        if (o instanceof Person1(Object name, String username)) {
            return name + ":" + username;
        }
        return null;
    }

    private static String test2(Object o) {
        if (o instanceof Person1(Object name)) {
            return "" + name;
        }
        return null;
    }

    private static String test3(Object o) {
        if (o instanceof Person1(var name, String username)) {
            return name + ":" + username;
        }
        return null;
    }

    private static String test4(Object o) {
        if (o instanceof Person1(var name)) {
            return "" + name;
        }
        return null;
    }

    private static String test5(Object o) {
        if (o instanceof Person1(Class name)) {
            return "" + name;
        }
        return null;
    }

    public record Person1(String name, String username) {

        public Person1(String name) {
            this(name, name);
        }

        public __matcher Person1(String name, String username) {
             name = this.name;
             username = this.username;
        }

        public __matcher Person1(Integer id, String username) {
             id = 0;
             username = this.username;
        }

        public __matcher Person1(String name) {
             name = this.name;
        }

        public __matcher Person1(Integer id) {
             id = 0;
        }
    }

}
