/**
 * @test
 * @enablePreview
 * @compile MatchSuper.java
 * @run main MatchSuper
 */
public class MatchSuper {

    public static void main(String... args) {
        Object o;

        o = new Impl("base", false, "impl", false);

        if (!(o instanceof Impl(var baseContent, var implContent)) || !"base".equals(baseContent) || !"impl".equals(implContent)) {
            throw new IllegalStateException();
        }

        switch (o) {
            case Impl(var baseContent2, var implContent2) -> {
                if (!"base".equals(baseContent2) || !"impl".equals(implContent2)) {
                    throw new IllegalStateException();
                }
            }
            default -> throw new IllegalStateException();
        }

        o = new Impl(null, true, null, false);

        if (o instanceof Impl(_, _)) {
            throw new IllegalStateException();
        }

        switch (o) {
            case MatchSuccessFail(_) ->
                    throw new IllegalStateException();
            default -> {}
        }

        o = new Impl(null, false, null, true);

        if (o instanceof Impl(_, _)) {
            throw new IllegalStateException();
        }

        switch (o) {
            case MatchSuccessFail(_) ->
                    throw new IllegalStateException();
            default -> {}
        }
    }

    public static class Base {
        private final String baseContent;
        private final boolean baseFail;

        public Base(String baseContent, boolean baseFail) {
            this.baseContent = baseContent;
            this.baseFail = baseFail;
        }

        public pattern Base(String baseContent) {
            if (baseFail) {
                match-fail();
            } else {
                match-success(baseContent);
            }
        }
    }
    public static class Impl extends Base {
        private final String implContent;
        private final boolean implFail;

        public Impl(String baseContent, boolean baseFail, String implContent, boolean implFail) {
            super(baseContent, baseFail);
            this.implContent = implContent;
            this.implFail = implFail;
        }

        public pattern Impl(String baseContent, String implContent) {
            if (implFail) {
                match-fail();
            } else {
                match-super(String baseContent);
                match-success(baseContent, implContent);
            }
        }
    }
}