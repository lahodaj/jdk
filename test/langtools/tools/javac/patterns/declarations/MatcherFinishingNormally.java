/**
 * @test
 * @enablePreview
 * @compile/fail/ref=MatcherFinishingNormally.out -XDrawDiagnostics MatcherFinishingNormally.java
 */
public class MatcherFinishingNormally {
    public pattern MatcherFinishingNormally(String content) {}
}