package explain.port;

/**
 * The one thing the explainer needs from a model: a reply to a system and a user message. Adapters exist for
 * Spring AI and LangChain4j; both reach the model through the reliability layer {@code llm-call-guard}.
 */
public interface ChatPort {
    /** @throws RuntimeException if the call fails; the explainer then falls back to the template */
    String chat(String system, String user);

    /** e.g. {@code spring-ai/qwen2.5:7b} */
    String name();
}
