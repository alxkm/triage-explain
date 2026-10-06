package explain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import triage.core.Cause;

class ValidatorTest {
    private final Incident jump = new Incident("t-1", "tank", 0, 400, 0.52, Cause.DATA_PATH, 1200, 1205, List.of("Hampel outlier"),
            List.of("level moved 7.43σ within 5 samples, above the plausible-rate bound 0.75σ"), new double[] {200, 210, 200}, "SPIKE",
            Cause.DATA_PATH);
    private final String facts = Facts.forExplanation(jump);

    private GroundingValidator.Result v(String reply) {
        return GroundingValidator.validate(jump, facts, reply);
    }

    @Test
    void aGroundedReplyPasses() {
        var r = v("{\"cause\":\"DATA_PATH\",\"explanation\":\"The level moved 7.4σ within 5 samples, faster than the 0.75σ bound a real process allows, "
                + "so the value was corrupted on its way; this is not a process change.\",\"next_check\":\"D1\"}");
        assertTrue(r.passed(), r.failed().toString());
    }

    @Test
    void eachRule() {
        assertEquals(List.of("R1-format"), v("The cause is DATA_PATH.").failed());
        assertTrue(v("{\"cause\":\"INSTRUMENT\",\"explanation\":\"x\",\"next_check\":\"D1\"}").failed().contains("R2-cause"));
        assertTrue(v("{\"cause\":\"DATA_PATH\",\"explanation\":\"It moved 9.1σ.\",\"next_check\":\"D1\"}").failed().get(0).startsWith("R3-number"));
        assertTrue(v("{\"cause\":\"DATA_PATH\",\"explanation\":\"x\",\"next_check\":\"I2\"}").failed().contains("R4-next-check"));
        assertTrue(v("{\"cause\":\"DATA_PATH\",\"explanation\":\"This looks like a sensor fault.\",\"next_check\":\"D1\"}").failed().get(0)
                .startsWith("R5-contradiction"));
        assertTrue(v("{\"cause\":\"DATA_PATH\",\"explanation\":\"" + "word ".repeat(81) + "\",\"next_check\":\"D1\"}").failed().contains("R6-length"));
    }

    @Test
    void numbersMayBeRoundedButNotChanged() {
        List<Double> allowed = GroundingValidator.numbers(facts);
        assertTrue(GroundingValidator.grounded("7.43", allowed));
        assertTrue(GroundingValidator.grounded("7.4", allowed));
        assertTrue(GroundingValidator.grounded("7", allowed));
        assertTrue(GroundingValidator.grounded("1205", allowed));
        assertFalse(GroundingValidator.grounded("7.5", allowed));
        assertFalse(GroundingValidator.grounded("14.86", allowed)); // 2 × 7.43: computed, not given
    }

    @Test
    void negatedMentionsOfOtherCausesAreAllowed() {
        // the rule is lexical: an implicit negation is not recognised and is rejected (a false rejection, reported in the paper)
        assertEquals("process_change", GroundingValidator.contradiction("The jump is too fast for a process change.", Cause.DATA_PATH));
        assertEquals("sensor_fault", GroundingValidator.contradiction("A sensor fault is likely.", Cause.DATA_PATH));
        assertNull(GroundingValidator.contradiction("This rules out a sensor fault.", Cause.DATA_PATH));
    }

    @Test
    void theTemplateAlwaysPasses() {
        for (Cause c : Cause.values()) {
            Incident i = new Incident("t", "thermal", 0, 150, 0.1, c, 10, 50, List.of(), List.of("level settled 2.29σ from baseline within the plausible rate"),
                    new double[] {1}, "X", c);
            Explanation e = TemplateExplainer.explain(i);
            String reply = "{\"cause\":\"" + c.name() + "\",\"explanation\":\"" + e.text().replace("\"", "'") + "\",\"next_check\":\"" + e.nextCheck() + "\"}";
            assertTrue(GroundingValidator.validate(i, Facts.forExplanation(i), reply).passed(), c + ": " + GroundingValidator.validate(i,
                    Facts.forExplanation(i), reply).failed());
        }
    }
}
