/**
 * @test
 * @enablePreview
 * @compile MatchSuccessFail.java
 * @run main MatchSuccessFail
 */
public class MatchSuccessFail {

    private final String content;
    private final boolean fail;

    public MatchSuccessFail(String content, boolean fail) {
        this.content = content;
        this.fail = fail;
    }

    public static void main(String... args) {
        Object o;

        o = new MatchSuccessFail("hello", false);

        if (!(o instanceof MatchSuccessFail(var content)) || !"hello".equals(content)) {
            throw new IllegalStateException();
        }

        switch (o) {
            case MatchSuccessFail(var c) -> {
                if (!"hello".equals(c)) {
                    throw new IllegalStateException();
                }
            }
            default -> throw new IllegalStateException();
        }

        o = new MatchSuccessFail("hello", true);

        if (o instanceof MatchSuccessFail(_)) {
            throw new IllegalStateException();
        }

        switch (o) {
            case MatchSuccessFail(_) ->
                    throw new IllegalStateException();
            default -> {}
        }
    }

    public pattern MatchSuccessFail(String content) {
        if (fail) {
            match-fail;
        } else {
            match-success(this.content);
        }
    }
}