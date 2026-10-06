package explain.port;

import callguard.Policy;
import callguard.langchain4j.GuardedHttpClientBuilder;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.SplittableRandom;

/** LangChain4j's OpenAI client against any OpenAI-compatible endpoint, with the guard as its HTTP client. */
public final class LangChain4jChatPort implements ChatPort {
    private final OpenAiChatModel model;
    private final String name;

    public LangChain4jChatPort(String baseUrl, String modelName, Policy policy, ObservationRegistry registry) {
        this.model = OpenAiChatModel.builder().baseUrl(baseUrl).apiKey("unused").modelName(modelName).temperature(0.0).seed(1).maxTokens(300)
                .maxRetries(0).httpClientBuilder(new GuardedHttpClientBuilder(policy, registry, new SplittableRandom(1))).build();
        this.name = "langchain4j/" + modelName;
    }

    @Override
    public String chat(String system, String user) {
        var r = model.chat(List.of(SystemMessage.from(system), UserMessage.from(user)));
        return r == null || r.aiMessage() == null ? "" : String.valueOf(r.aiMessage().text());
    }

    @Override
    public String name() {
        return name;
    }
}
