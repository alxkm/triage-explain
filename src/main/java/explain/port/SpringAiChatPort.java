package explain.port;

import callguard.Policy;
import callguard.okhttp.GuardInterceptor;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import java.util.SplittableRandom;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/** Spring AI's OpenAI client against any OpenAI-compatible endpoint (here: a local Ollama), with the guard installed. */
public final class SpringAiChatPort implements ChatPort {
    private final OpenAiChatModel model;
    private final String name;

    public SpringAiChatPort(String baseUrl, String modelName, Policy policy, ObservationRegistry registry) {
        GuardInterceptor guard = new GuardInterceptor(policy, registry, "spring-ai", new SplittableRandom(1));
        this.model = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder().baseUrl(baseUrl).apiKey("unused").model(modelName).temperature(0.0).seed(1).maxTokens(300)
                        .maxRetries(0).timeout(Duration.ofMinutes(10)).build())
                .observationRegistry(registry)
                .httpClientBuilderCustomizer(b -> b.interceptor(guard))
                .build();
        this.name = "spring-ai/" + modelName;
    }

    @Override
    public String chat(String system, String user) {
        var r = model.call(new Prompt(List.of(new SystemMessage(system), new UserMessage(user))));
        return r == null || r.getResult() == null ? "" : String.valueOf(r.getResult().getOutput().getText());
    }

    @Override
    public String name() {
        return name;
    }
}
